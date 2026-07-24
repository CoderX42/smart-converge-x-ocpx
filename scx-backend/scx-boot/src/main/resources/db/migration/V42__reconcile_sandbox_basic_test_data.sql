-- Reconcile the overlapping V25/V30 sandbox seeds by business key.
-- V30 intentionally reuses V25 codes, so ON DUPLICATE KEY UPDATE retains the
-- earlier primary keys. Keep all demo facts pointed at the retained chain.

SET @demo_partner_id = (
  SELECT id FROM scx_partner
  WHERE media_id='media_sandbox' AND code='demo-upstream'
  LIMIT 1
);
SET @demo_customer_id = (
  SELECT id FROM scx_upstream_customer
  WHERE media_id='media_sandbox' AND code='demo-customer'
  LIMIT 1
);
SET @demo_document_id = (
  SELECT id FROM scx_integration_document
  WHERE media_id='media_sandbox' AND id IN ('document_demo_mvp','document_demo')
  ORDER BY (id='document_demo_mvp') DESC
  LIMIT 1
);
SET @demo_channel_id = (
  SELECT id FROM scx_media_channel
  WHERE media_id='media_sandbox' AND code='demo-channel'
  LIMIT 1
);

UPDATE scx_product
SET customer_id=@demo_customer_id,
    name='MVP 演示产品',
    description='用于追踪、归因和日报测试',
    status='ACTIVE',
    update_by='system',
    update_time=NOW()
WHERE media_id='media_sandbox'
  AND code='demo-product'
  AND @demo_customer_id IS NOT NULL;

SET @demo_product_id = (
  SELECT id FROM scx_product
  WHERE media_id='media_sandbox' AND code='demo-product'
  LIMIT 1
);

UPDATE scx_tracking_link
SET customer_id=@demo_customer_id,
    document_id=@demo_document_id,
    name='MVP 演示追踪链接',
    target_url='https://example.invalid/scx/mvp-landing',
    download_url='https://example.invalid/scx/mvp.apk',
    template_text='https://example.invalid/scx/mvp-landing?task={task_code}',
    status='ACTIVE',
    update_by='system',
    update_time=NOW()
WHERE media_id='media_sandbox'
  AND code='demo-link'
  AND @demo_customer_id IS NOT NULL
  AND @demo_document_id IS NOT NULL;

SET @demo_link_id = (
  SELECT id FROM scx_tracking_link
  WHERE media_id='media_sandbox' AND code='demo-link'
  LIMIT 1
);

UPDATE scx_tracking_task
SET link_id=@demo_link_id,
    channel_id=@demo_channel_id,
    name='MVP 演示监测任务',
    dedup_window_sec=3600,
    attribution_window_hours=168,
    daily_conversion_limit=10000,
    status='ACTIVE',
    update_by='system',
    update_time=NOW()
WHERE media_id='media_sandbox'
  AND code='demo-task'
  AND @demo_link_id IS NOT NULL
  AND @demo_channel_id IS NOT NULL;

SET @demo_task_id = (
  SELECT id FROM scx_tracking_task
  WHERE media_id='media_sandbox' AND code='demo-task'
  LIMIT 1
);

UPDATE scx_raw_event
SET task_id=@demo_task_id
WHERE id IN ('raw_demo_mvp_click','raw_demo_mvp_activate')
  AND @demo_task_id IS NOT NULL;

UPDATE scx_conversion_identity
SET task_id=@demo_task_id
WHERE id='identity_demo_mvp_activate'
  AND @demo_task_id IS NOT NULL;

UPDATE scx_report_daily
SET media_id='media_sandbox',
    partner_id=@demo_partner_id,
    customer_id=@demo_customer_id,
    product_id=@demo_product_id,
    link_id=@demo_link_id,
    document_id=@demo_document_id,
    task_id=@demo_task_id,
    channel_id=@demo_channel_id,
    calculated_at=NOW(3)
WHERE id='report_daily_demo_mvp'
  AND @demo_partner_id IS NOT NULL
  AND @demo_customer_id IS NOT NULL
  AND @demo_product_id IS NOT NULL
  AND @demo_link_id IS NOT NULL
  AND @demo_document_id IS NOT NULL
  AND @demo_task_id IS NOT NULL
  AND @demo_channel_id IS NOT NULL;
