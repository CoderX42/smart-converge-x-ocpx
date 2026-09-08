package com.yzytech.scx.ocpx.rta.relay;

import com.yzytech.scx.ocpx.rta.contract.RtaContract.*;
import com.yzytech.scx.ocpx.rta.contract.RtaState;

import com.yzytech.scx.ocpx.persistence.mapper.RelaySqlMapper;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class RelayRepository {

	private final RelaySqlMapper sqlMapper;

	public RelayRepository(RelaySqlMapper sqlMapper) {
		this.sqlMapper = sqlMapper;
	}

	public Media mediaByCredential(String ref) {
		if (ref == null || !ref.matches("[A-Za-z0-9_-]{1,64}"))
			throw new RelayException(401, "RTA_MEDIA_UNAVAILABLE");
		List<Media> rows = sqlMapper.selectMediaByCredentialRtaMediaAccess(ref)
			.stream()
			.map(rs -> new Media(rs.mediaId(), rs.notifyUrl(), rs.credentialRef(), rs.version(), rs.enabled()))
			.toList();
		if (rows.size() != 1)
			throw new RelayException(401, "RTA_MEDIA_UNAVAILABLE");
		return rows.get(0);
	}

	public Upstream upstream(String column, String value) {
		if (!List.of("id", "access_code").contains(column))
			throw new IllegalArgumentException();
		List<Upstream> rows = sqlMapper.selectUpstream(column, value)
			.stream()
			.map(rs -> new Upstream(rs.id(), rs.advertiserId(), rs.accessCode(), rs.endpoint(), rs.credentialRef(),
					rs.version(), rs.timeoutMs(), rs.admissionSeconds(), true, rs.protocol(), rs.configSchemaVersion(),
					config(rs.protocolConfig())))
			.toList();
		if (rows.size() != 1)
			throw new RelayException(400, "RTA_UPSTREAM_UNAVAILABLE");
		return rows.get(0);
	}

	public Binding binding(String id, String mediaId) {
		List<Binding> rows = sqlMapper.selectBindingMonitorTask(id, mediaId)
			.stream()
			.map(rs -> new Binding(rs.id(), rs.advertiserId(), rs.mediaId(), rs.promotionLinkId(), rs.upstreamId(),
					rs.productId(), rs.externalTaskId(), rs.version()))
			.toList();
		if (rows.size() != 1)
			throw new RelayException(400, "RTA_TASK_UNAVAILABLE");
		return rows.get(0);
	}

	public boolean taskRequiresRta(String taskId) {
		List<Boolean> rows = sqlMapper.selectTaskRequiresRtaMonitorTask(taskId)
			.stream()
			.map(rs -> rs.rtaEnabled())
			.toList();
		return rows.size() == 1 && rows.get(0);
	}

	@SuppressWarnings("unchecked")
	private java.util.Map<String, String> config(String json) {
		return json == null ? java.util.Map.of() : RelayJson.read(json, java.util.Map.class);
	}

	public void validateRoute(Upstream upstream, List<Binding> bindings) {
		if (!com.yzytech.scx.ocpx.rta.contract.RtaProtocols.require(upstream.protocol())
			.routeIdentityFields()
			.isEmpty())
			for (var b : bindings) {
				try {
					com.yzytech.scx.ocpx.rta.contract.RtaProtocols.require(upstream.protocol())
						.validateRoute(sqlMapper.selectLink(b.linkId()), upstream.protocolConfig());
				}
				catch (IllegalArgumentException e) {
					throw new RelayException(403, "RTA_PLAN_MISMATCH");
				}
			}
	}

	public void current(RtaState state, Binding snapshot) {
		Media media = mediaByCredential(state.getMedia().credentialRef());
		Upstream upstream = upstream("id", state.getUpstream().id());
		Binding binding = binding(snapshot.taskId(), state.getMediaId());
		validateRoute(upstream, List.of(binding));
		if (!media.equals(state.getMedia()) || !upstream.equals(state.getUpstream()) || !binding.equals(snapshot))
			throw new RelayException(403, "RTA_BINDING_CHANGED");
	}

}
