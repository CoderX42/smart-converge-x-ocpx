-- V75: normalize built-in adapter capabilities for protocol-driven route publishing.
-- The management service now validates URLs from these fields instead of hard-coding adapter codes.

UPDATE scx_ocpx_adapter
SET capabilities_json=JSON_OBJECT(
      'contractVersion','1.0','protocolKind','DEDICATED',
      'feedbackMethod','GET','feedbackEvents',JSON_ARRAY('CLICK'),
      'callbackMethod','GET','auth','NONE','callback',TRUE,'timeoutMs',3000,
      'supportedMacros',JSON_ARRAY(
        '__IDFA__','__IDFA_MD5__','__IMEI__','__IMEI_MD5__','__OAID__','__OAID_MD5__','__IP__',
        '__CAID__','__CAID_LIST__','__ANDROIDID__','__ANDROID_ID__','__ANDROIDID_MD5__',
        '__ANDROID_ID_MD5__','__AAID__','__UA__','__OS__','__MODEL__','__CALLBACK__','__CLICKID__',
        '__CAMPAIGNID__','__ADGROUPID__','__CREATIVEID__','__ADVERTISERID__'),
      'requiredUrlMacros',JSON_ARRAY('__CALLBACK__'),
      'requiredAnyUrlMacros',JSON_ARRAY()
    ),
    update_time=NOW(3)
WHERE adapter_code='COCO' AND direction='UPSTREAM';

UPDATE scx_ocpx_adapter
SET capabilities_json=JSON_OBJECT(
      'contractVersion','1.0','protocolKind','GET_TEMPLATE',
      'feedbackMethod','GET','feedbackEvents',JSON_ARRAY('SHOW','CLICK'),
      'callbackMethod','GET','auth','NONE','callback',TRUE,'timeoutMs',3000,
      'supportedMacros',JSON_ARRAY(
        '__EVENT__','__EVENT_TIME__','__EVENT_ID__','__TS__','__MTS__','__REQUEST_ID__','__CHANNEL_ID__',
        '__DSP_ID__','__LINK_ID__','__TASK_ID__','__MONITOR_ID__','__BIZ_ID__','__CAMPAIGN_ID__',
        '__ADGROUP_ID__','__CREATIVE_ID__','__CREATIVEID__','__CLICKID__','__IDFA__','__IDFA_MD5__',
        '__IDFA__MD5__','__IMEI__','__IMEI_MD5__','__IMEI__MD5__','__OAID__','__OAID_MD5__',
        '__OAID__MD5__','__CAID__','__CAID_LIST__','__ANDROID_ID__','__ANDROID_ID_MD5__','__AAID__',
        '__GAID__','__IDFV__','__IP__','__UA__','__OS__','__MODEL__','__CALLBACK__','__CALLBACK_URL__'),
      'requiredUrlMacros',JSON_ARRAY(),
      'requiredAnyUrlMacros',JSON_ARRAY(JSON_ARRAY('__CALLBACK__','__CALLBACK_URL__')),
      'compatibility','sgyz-ocpx-base-flow'
    ),
    update_time=NOW(3)
WHERE adapter_code='TEMPLATE_GET' AND direction='UPSTREAM';

UPDATE scx_ocpx_adapter
SET capabilities_json=JSON_OBJECT(
      'contractVersion','1.0','protocolKind','STANDARD_JSON',
      'feedbackMethod','POST','feedbackEvents',JSON_ARRAY(
        'SHOW','CLICK','ACTIVATE','EVOKE','LOGIN','REGISTER','NEXT_DAY_RETENTION','PAYMENT',
        'CUSTOMER_ACQUISITION','VISIT','AUTHENTICATION','ORDER','FIRST_ORDER','COMPLETE_ORDER',
        'RETENTION','REACTIVATION','FIRST_REACTIVATION'),
      'callbackMethod','POST','auth','HMAC','callback',TRUE,'timeoutMs',3000,
      'supportedMacros',JSON_ARRAY(),'requiredUrlMacros',JSON_ARRAY(),'requiredAnyUrlMacros',JSON_ARRAY()
    ),
    update_time=NOW(3)
WHERE adapter_code='STANDARD' AND direction='DOWNSTREAM';

UPDATE scx_ocpx_adapter
SET capabilities_json=JSON_OBJECT(
      'contractVersion','1.0','protocolKind','GET_TEMPLATE',
      'feedbackMethod','POST','feedbackEvents',JSON_ARRAY(
        'SHOW','CLICK','ACTIVATE','EVOKE','LOGIN','REGISTER','NEXT_DAY_RETENTION','PAYMENT',
        'CUSTOMER_ACQUISITION','VISIT','AUTHENTICATION','ORDER','FIRST_ORDER','COMPLETE_ORDER',
        'RETENTION','REACTIVATION','FIRST_REACTIVATION'),
      'callbackMethod','GET','auth','HMAC','callback',TRUE,'timeoutMs',3000,
      'supportedMacros',JSON_ARRAY('__EVENT__','__EVENT_ID__','__SOURCE_EVENT_ID__','__CLICKID__',
        '__MONITOR_ID__','__CHANNEL_ID__','__AMOUNT__','__AMOUNT_RAW__','__TS__'),
      'requiredUrlMacros',JSON_ARRAY('__EVENT__','__EVENT_ID__'),
      'requiredAnyUrlMacros',JSON_ARRAY()
    ),
    update_time=NOW(3)
WHERE adapter_code='TEMPLATE_GET_DOWNSTREAM' AND direction='DOWNSTREAM';
