package com.yzytech.scx.adx.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.adx.dto.RuntimeViews;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdxRuntimeService {

	private final JdbcTemplate jdbcTemplate;

	private final StringRedisTemplate redisTemplate;

	private final int partitionCount;

	public AdxRuntimeService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
			@Value("${scx.ocpx.partition-count:64}") int partitionCount) {
		this.jdbcTemplate = jdbcTemplate;
		this.redisTemplate = redisTemplate;
		this.partitionCount = partitionCount;
	}

	public RuntimeViews.Overview overview() {
		Map<String, Long> statuses = new LinkedHashMap<>();
		jdbcTemplate.query("SELECT status,COUNT(*) FROM scx_ocpx_delivery_job GROUP BY status",
				(RowCallbackHandler) rs -> statuses.put(rs.getString(1), rs.getLong(2)));
		Map<String, Long> directions = new LinkedHashMap<>();
		jdbcTemplate.query("SELECT direction,COUNT(*) FROM scx_ocpx_delivery_job GROUP BY direction",
				(RowCallbackHandler) rs -> directions.put(rs.getString(1), rs.getLong(2)));
		Long dlq = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_ingress_dlq WHERE status='PENDING'",
				Long.class);
		return new RuntimeViews.Overview(statuses.getOrDefault("PENDING", 0L), statuses.getOrDefault("RETRY", 0L),
				statuses.getOrDefault("DEAD", 0L), statuses.getOrDefault("SUCCESS", 0L), dlq == null ? 0 : dlq,
				feedbackPending(), directions);
	}

	public Page<RuntimeViews.Delivery> deliveries(long current, long size, String status, String direction) {
		String where = " WHERE 1=1" + (StringUtils.hasText(status) ? " AND status=?" : "")
				+ (StringUtils.hasText(direction) ? " AND direction=?" : "");
		List<Object> args = new java.util.ArrayList<>();
		if (StringUtils.hasText(status))
			args.add(status);
		if (StringUtils.hasText(direction))
			args.add(direction);
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_delivery_job" + where, Long.class,
				args.toArray());
		args.add(size);
		args.add((current - 1) * size);
		List<RuntimeViews.Delivery> rows = jdbcTemplate.query("""
				SELECT id,event_id,source_event_id,direction,adapter_code,status,attempt_count,next_retry_at,last_error,
				       create_time,update_time FROM scx_ocpx_delivery_job
				""" + where + " ORDER BY create_time DESC LIMIT ? OFFSET ?",
				(rs, rowNum) -> new RuntimeViews.Delivery(rs.getString(1), rs.getString(2), rs.getString(3),
						rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7),
						rs.getTimestamp(8).toLocalDateTime(), rs.getString(9), rs.getTimestamp(10).toLocalDateTime(),
						rs.getTimestamp(11).toLocalDateTime()),
				args.toArray());
		Page<RuntimeViews.Delivery> page = new Page<>(current, size, total == null ? 0 : total);
		page.setRecords(rows);
		return page;
	}

	public Page<RuntimeViews.IngressDlq> ingressDlq(long current, long size, String status) {
		String where = StringUtils.hasText(status) ? " WHERE status=?" : "";
		List<Object> args = new java.util.ArrayList<>();
		if (StringUtils.hasText(status)) {
			args.add(status);
		}
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scx_ocpx_ingress_dlq" + where, Long.class,
				args.toArray());
		args.add(size);
		args.add((current - 1) * size);
		List<RuntimeViews.IngressDlq> rows = jdbcTemplate.query("""
				SELECT id,partition_no,stream_record_id,event_id,reason,status,create_time,replayed_at
				FROM scx_ocpx_ingress_dlq
				""" + where + " ORDER BY create_time DESC LIMIT ? OFFSET ?",
				(rs, rowNum) -> new RuntimeViews.IngressDlq(rs.getString(1), rs.getInt(2), rs.getString(3),
						rs.getString(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toLocalDateTime(),
						rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toLocalDateTime()),
				args.toArray());
		Page<RuntimeViews.IngressDlq> page = new Page<>(current, size, total == null ? 0 : total);
		page.setRecords(rows);
		return page;
	}

	@Transactional
	public void replayDelivery(String id) {
		int updated = jdbcTemplate.update("""
				UPDATE scx_ocpx_delivery_job SET status='PENDING',attempt_count=0,next_retry_at=NOW(3),
				 lease_owner=NULL,lease_until=NULL,last_error=NULL,update_time=NOW(3)
				WHERE id=? AND status='DEAD'
				""", id);
		if (updated == 0)
			throw new IllegalArgumentException("仅死亡任务可以重放");
	}

	public void replayIngressDlq(String id) {
		List<IngressDlqPayload> rows = jdbcTemplate.query("""
				SELECT partition_no,event_id,envelope,reason FROM scx_ocpx_ingress_dlq
				WHERE id=? AND status='PENDING'
				""",
				(rs, rowNum) -> new IngressDlqPayload(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)),
				id);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("仅待处理入口死信可以重放");
		}
		IngressDlqPayload payload = rows.get(0);
		if (payload.reason().startsWith("INVALID_ENVELOPE")) {
			throw new IllegalArgumentException("无效协议死信必须修正数据后重新接入，不能直接重放");
		}
		String stream = "scx:ocpx:{feedback:" + payload.partition() + "}:stream";
		redisTemplate.opsForStream()
			.add(stream, Map.of("event_id", payload.eventId() == null ? "" : payload.eventId(), "envelope",
					payload.envelope()));
		jdbcTemplate.update(
				"UPDATE scx_ocpx_ingress_dlq SET status='REPLAYED',replayed_at=NOW(3) WHERE id=? AND status='PENDING'",
				id);
	}

	private long feedbackPending() {
		long pending = 0;
		try {
			for (int partition = 0; partition < partitionCount; partition++) {
				String key = "scx:ocpx:{feedback:" + partition + "}:stream";
				Long size = redisTemplate.opsForStream().size(key);
				pending += size == null ? 0 : size;
			}
		}
		catch (Exception ignored) {
			return -1;
		}
		return pending;
	}

	private record IngressDlqPayload(int partition, String eventId, String envelope, String reason) {
	}

}
