package com.yzytech.scx.reporting.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.reporting.dto.ReportBreakdown;
import com.yzytech.scx.reporting.dto.ReportTrendPoint;
import com.yzytech.scx.reporting.entity.ReportDaily;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
@Mapper
public interface ReportDailyMapper extends BaseMapper<ReportDaily> {
    @Insert("""
        INSERT INTO scx_report_daily
          (id,report_run_id,report_date,timezone,media_id,partner_id,customer_id,product_id,link_id,document_id,task_id,channel_id,impression_count,
           unique_click_count,eligible_click_count,deduplicated_click_count,attributed_activate_count,
           attributed_register_count,attributed_pay_count,attributed_order_count,next_day_return_count,retention_3d_count,
           retention_7d_count,first_activate_count,first_order_count,reactivate_count,custom_count,credit_count,revision,calculated_at)
        SELECT REPLACE(UUID(),'-',''),#{runId},#{date},'Asia/Shanghai',t.media_id,l.partner_id,l.customer_id,l.product_id,l.id,l.document_id,t.id,t.channel_id,
          SUM(CASE WHEN e.event_type='IMPRESSION' THEN 1 ELSE 0 END),
          SUM(CASE WHEN e.event_type='CLICK' THEN 1 ELSE 0 END),
          SUM(CASE WHEN cd.status='ELIGIBLE' THEN 1 ELSE 0 END),
          SUM(CASE WHEN cd.status='DEDUPLICATED' THEN 1 ELSE 0 END),
          SUM(CASE WHEN e.event_type='ACTIVATE' AND a.status='ATTRIBUTED' AND a.current_flag=1 AND COALESCE(a.review_status,'PENDING') != 'REDUCED' AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END),
          SUM(CASE WHEN e.event_type='REGISTER' AND a.status='ATTRIBUTED' AND a.current_flag=1 AND COALESCE(a.review_status,'PENDING') != 'REDUCED' AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END),
          SUM(CASE WHEN e.event_type='PAY' AND a.status='ATTRIBUTED' AND a.current_flag=1 AND COALESCE(a.review_status,'PENDING') != 'REDUCED' AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END),
          SUM(CASE WHEN e.event_type='ORDER' AND a.status='ATTRIBUTED' AND a.current_flag=1 AND COALESCE(a.review_status,'PENDING') != 'REDUCED' AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END),
          SUM(CASE WHEN e.event_type='NEXT_DAY_RETURN' AND a.status='ATTRIBUTED' AND a.current_flag=1 AND COALESCE(a.review_status,'PENDING') != 'REDUCED' AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END),
          SUM(CASE WHEN e.event_type='RETAIN_3D' AND a.status='ATTRIBUTED' AND a.current_flag=1 AND COALESCE(a.review_status,'PENDING') != 'REDUCED' AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END),
          SUM(CASE WHEN e.event_type='RETAIN_7D' AND a.status='ATTRIBUTED' AND a.current_flag=1 AND COALESCE(a.review_status,'PENDING') != 'REDUCED' AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END),
          SUM(CASE WHEN e.event_type='FIRST_ACTIVATE' AND a.status='ATTRIBUTED' AND a.current_flag=1 AND COALESCE(a.review_status,'PENDING') != 'REDUCED' AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END),
          SUM(CASE WHEN e.event_type='FIRST_ORDER' AND a.status='ATTRIBUTED' AND a.current_flag=1 AND COALESCE(a.review_status,'PENDING') != 'REDUCED' AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END),
          SUM(CASE WHEN e.event_type='REACTIVATE' AND a.status='ATTRIBUTED' AND a.current_flag=1 AND COALESCE(a.review_status,'PENDING') != 'REDUCED' AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END),
          SUM(CASE WHEN e.event_type='CUSTOM' AND a.status='ATTRIBUTED' AND a.current_flag=1 AND COALESCE(a.review_status,'PENDING') != 'REDUCED' AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END),
          SUM(CASE WHEN e.event_type='CREDIT' AND a.status='ATTRIBUTED' AND a.current_flag=1 AND COALESCE(a.review_status,'PENDING') != 'REDUCED' AND (COALESCE(a.over_limit,0)=0 OR a.review_status='APPROVED') THEN 1 ELSE 0 END),
          1,NOW(3)
        FROM scx_raw_event e
        JOIN scx_tracking_task t ON t.id=e.task_id
        JOIN scx_tracking_link l ON l.id=t.link_id
        LEFT JOIN scx_click_dedup_result cd ON cd.click_event_id=e.id
        LEFT JOIN scx_attribution_result a ON a.conversion_event_id=e.id AND a.current_flag=1
        WHERE DATE(e.occurred_at)=#{date} AND t.media_id=#{mediaId} AND e.received_at <= #{cutoff}
        GROUP BY t.media_id,l.partner_id,l.customer_id,l.product_id,l.id,l.document_id,t.id,t.channel_id
        """)
    int aggregate(@Param("runId") String runId, @Param("date") LocalDate date,
            @Param("mediaId") String mediaId, @Param("cutoff") LocalDateTime cutoff);

    @org.apache.ibatis.annotations.Select("""
        <script>
        SELECT d.*
        FROM scx_report_daily d
        JOIN scx_report_run r ON r.id=d.report_run_id
        WHERE d.media_id=#{mediaId}
          AND d.report_date BETWEEN #{startDate} AND #{endDate}
          <if test="taskRestricted">
            <choose>
              <when test="taskIds != null and !taskIds.isEmpty()">
                AND d.task_id IN
                <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
              </when>
              <otherwise>AND 1=0</otherwise>
            </choose>
          </if>
          AND r.status='SUCCEEDED'
          AND r.id=(
            SELECT r2.id FROM scx_report_run r2
            WHERE r2.report_date=d.report_date AND r2.status='SUCCEEDED'
              AND EXISTS (SELECT 1 FROM scx_report_daily d2 WHERE d2.report_run_id=r2.id AND d2.media_id=#{mediaId})
            ORDER BY r2.finished_at DESC LIMIT 1
          )
        ORDER BY d.report_date,d.task_id
        LIMIT #{limit}
        </script>
        """)
    List<ReportDaily> selectForExport(@Param("mediaId") String mediaId, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate, @Param("limit") int limit,
            @Param("taskRestricted") boolean taskRestricted, @Param("taskIds") List<String> taskIds);

    @org.apache.ibatis.annotations.Select("""
        <script>
        SELECT
        <choose>
          <when test="dimension == 'DATE'">CAST(d.report_date AS CHAR) AS dimension_id, CAST(d.report_date AS CHAR) AS dimension_name</when>
          <when test="dimension == 'MEDIA'">d.media_id AS dimension_id, MAX(COALESCE(m.name, d.media_id)) AS dimension_name</when>
          <when test="dimension == 'PARTNER'">d.partner_id AS dimension_id, MAX(COALESCE(p.name, d.partner_id)) AS dimension_name</when>
          <when test="dimension == 'CUSTOMER'">d.customer_id AS dimension_id, MAX(COALESCE(c.name, d.customer_id)) AS dimension_name</when>
          <when test="dimension == 'PRODUCT'">d.product_id AS dimension_id, MAX(COALESCE(pr.name, d.product_id)) AS dimension_name</when>
          <when test="dimension == 'LINK'">d.link_id AS dimension_id, MAX(COALESCE(l.name, d.link_id)) AS dimension_name</when>
          <when test="dimension == 'DOCUMENT'">d.document_id AS dimension_id, MAX(COALESCE(doc.name, d.document_id)) AS dimension_name</when>
          <when test="dimension == 'CHANNEL'">d.channel_id AS dimension_id, MAX(COALESCE(ch.name, d.channel_id)) AS dimension_name</when>
          <otherwise>d.task_id AS dimension_id, MAX(COALESCE(t.name, d.task_id)) AS dimension_name</otherwise>
        </choose>,
        SUM(d.impression_count) AS impression_count,
        SUM(d.unique_click_count) AS unique_click_count,
        SUM(d.eligible_click_count) AS eligible_click_count,
        SUM(d.deduplicated_click_count) AS deduplicated_click_count,
        SUM(d.attributed_activate_count) AS attributed_activate_count,
        SUM(d.attributed_register_count) AS attributed_register_count,
        SUM(d.attributed_pay_count) AS attributed_pay_count
        ,SUM(d.attributed_order_count) AS attributed_order_count
        ,SUM(d.next_day_return_count) AS next_day_return_count
        ,SUM(d.retention_3d_count) AS retention_3d_count
        ,SUM(d.retention_7d_count) AS retention_7d_count
        ,SUM(d.first_activate_count) AS first_activate_count
        ,SUM(d.first_order_count) AS first_order_count
        ,SUM(d.reactivate_count) AS reactivate_count
        ,SUM(d.custom_count) AS custom_count
        ,SUM(d.credit_count) AS credit_count
        FROM scx_report_daily d
        JOIN scx_report_run r ON r.id = d.report_run_id
        LEFT JOIN scx_partner p ON p.id = d.partner_id
        LEFT JOIN scx_media m ON m.id = d.media_id
        LEFT JOIN scx_upstream_customer c ON c.id = d.customer_id
        LEFT JOIN scx_product pr ON pr.id = d.product_id
        LEFT JOIN scx_tracking_link l ON l.id = d.link_id
        LEFT JOIN scx_integration_document doc ON doc.id = d.document_id
        LEFT JOIN scx_tracking_task t ON t.id = d.task_id
        LEFT JOIN scx_media_channel ch ON ch.id = d.channel_id
        WHERE d.media_id = #{mediaId}
          AND d.report_date BETWEEN #{startDate} AND #{endDate}
          <if test="customerId != null and !customerId.isBlank()">AND d.customer_id = #{customerId}</if>
          <if test="productId != null and !productId.isBlank()">AND d.product_id = #{productId}</if>
          <if test="linkId != null and !linkId.isBlank()">AND d.link_id = #{linkId}</if>
          <if test="taskId != null and !taskId.isBlank()">AND d.task_id = #{taskId}</if>
          <if test="channelId != null and !channelId.isBlank()">AND d.channel_id = #{channelId}</if>
          <if test="documentId != null and !documentId.isBlank()">AND d.document_id = #{documentId}</if>
          <if test="taskRestricted">
            <choose>
              <when test="taskIds != null and !taskIds.isEmpty()">
                AND d.task_id IN
                <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
              </when>
              <otherwise>AND 1=0</otherwise>
            </choose>
          </if>
          AND r.status = 'SUCCEEDED'
          AND r.id = (
            SELECT r2.id FROM scx_report_run r2
            WHERE r2.report_date = d.report_date AND r2.status = 'SUCCEEDED'
              AND EXISTS (SELECT 1 FROM scx_report_daily d2 WHERE d2.report_run_id = r2.id AND d2.media_id = #{mediaId})
            ORDER BY r2.finished_at DESC LIMIT 1
          )
        GROUP BY
        <choose>
          <when test="dimension == 'DATE'">d.report_date</when>
          <when test="dimension == 'MEDIA'">d.media_id</when>
          <when test="dimension == 'PARTNER'">d.partner_id</when>
          <when test="dimension == 'CUSTOMER'">d.customer_id</when>
          <when test="dimension == 'PRODUCT'">d.product_id</when>
          <when test="dimension == 'LINK'">d.link_id</when>
          <when test="dimension == 'DOCUMENT'">d.document_id</when>
          <when test="dimension == 'CHANNEL'">d.channel_id</when>
          <otherwise>d.task_id</otherwise>
        </choose>
        ORDER BY
        <choose>
          <when test="dimension == 'DATE'">d.report_date DESC</when>
          <otherwise>unique_click_count DESC</otherwise>
        </choose>
        </script>
        """)
    List<ReportBreakdown> selectBreakdown(@Param("mediaId") String mediaId, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate, @Param("dimension") String dimension,
            @Param("customerId") String customerId, @Param("productId") String productId, @Param("linkId") String linkId,
            @Param("taskId") String taskId, @Param("channelId") String channelId, @Param("documentId") String documentId,
            @Param("taskRestricted") boolean taskRestricted, @Param("taskIds") List<String> taskIds);

    @org.apache.ibatis.annotations.Select("""
        <script>
        SELECT d.report_date,
          SUM(d.impression_count) AS impression_count,
          SUM(d.unique_click_count) AS unique_click_count,
          SUM(d.eligible_click_count) AS eligible_click_count,
          SUM(d.attributed_activate_count) AS attributed_activate_count,
          SUM(d.attributed_register_count) AS attributed_register_count,
          SUM(d.attributed_pay_count) AS attributed_pay_count,
          SUM(d.attributed_order_count) AS attributed_order_count,
          SUM(d.next_day_return_count) AS next_day_return_count
        FROM scx_report_daily d
        JOIN scx_report_run r ON r.id=d.report_run_id
        WHERE d.media_id=#{mediaId}
          AND d.report_date BETWEEN #{startDate} AND #{endDate}
          <if test="taskRestricted">
            <choose>
              <when test="taskIds != null and !taskIds.isEmpty()">
                AND d.task_id IN
                <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">#{taskId}</foreach>
              </when>
              <otherwise>AND 1=0</otherwise>
            </choose>
          </if>
          AND r.status='SUCCEEDED'
          AND r.id=(
            SELECT r2.id FROM scx_report_run r2
            WHERE r2.report_date=d.report_date AND r2.status='SUCCEEDED'
              AND EXISTS (SELECT 1 FROM scx_report_daily d2 WHERE d2.report_run_id=r2.id AND d2.media_id=#{mediaId})
            ORDER BY r2.finished_at DESC LIMIT 1
          )
        GROUP BY d.report_date
        ORDER BY d.report_date
        </script>
        """)
    List<ReportTrendPoint> selectTrend(@Param("mediaId") String mediaId, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate, @Param("taskRestricted") boolean taskRestricted,
            @Param("taskIds") List<String> taskIds);
}
