package com.yzytech.scx.ocpx.contract;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import java.util.TreeMap;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FeedbackReqV1 {

	@JsonProperty("schema_version")
	private String schemaVersion = "1.0";

	@JsonProperty("event_id")
	private String eventId;

	@NotBlank
	private String event;

	@JsonProperty("channel_id")
	private String channelId;

	@JsonProperty("dsp_id")
	private String dspId;

	@JsonProperty("link_id")
	private String linkId;

	@JsonProperty("task_id")
	private String taskId;

	@JsonProperty("monitor_id")
	private String monitorId;

	@JsonProperty("callback_url")
	private String callbackUrl;

	@JsonProperty("event_time")
	private Long eventTime;

	private String ip;

	private String ua;

	private String os;

	private String model;

	private String clickid;

	@JsonProperty("campaign_id")
	private String campaignId;

	@JsonProperty("adgroup_id")
	private String adgroupId;

	@JsonProperty("creative_id")
	private String creativeId;

	@JsonProperty("advertiser_id")
	private String advertiserId;

	private String idfa;

	@JsonProperty("idfa_md5")
	private String idfaMd5;

	private String imei;

	@JsonProperty("imei_md5")
	private String imeiMd5;

	private String oaid;

	@JsonProperty("oaid_md5")
	private String oaidMd5;

	private String caid;

	@JsonProperty("android_id")
	private String androidId;

	@JsonProperty("android_idmd5")
	private String androidIdMd5;

	private String aaid;

	private String gaid;

	private String idfv;

	private final Map<String, Object> extensions = new TreeMap<>();

	@JsonAnySetter
	public void extension(String name, Object value) {
		extensions.put(name, value);
	}

	@JsonAnyGetter
	public Map<String, Object> getExtensions() {
		return extensions;
	}

	public String getSchemaVersion() {
		return schemaVersion;
	}

	public void setSchemaVersion(String schemaVersion) {
		this.schemaVersion = schemaVersion;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getEvent() {
		return event;
	}

	public void setEvent(String event) {
		this.event = event;
	}

	public String getChannelId() {
		return channelId;
	}

	public void setChannelId(String channelId) {
		this.channelId = channelId;
	}

	public String getDspId() {
		return dspId;
	}

	public void setDspId(String dspId) {
		this.dspId = dspId;
	}

	public String getLinkId() {
		return linkId;
	}

	public void setLinkId(String linkId) {
		this.linkId = linkId;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public String getMonitorId() {
		return monitorId;
	}

	public void setMonitorId(String monitorId) {
		this.monitorId = monitorId;
	}

	public String getCallbackUrl() {
		return callbackUrl;
	}

	public void setCallbackUrl(String callbackUrl) {
		this.callbackUrl = callbackUrl;
	}

	public Long getEventTime() {
		return eventTime;
	}

	public void setEventTime(Long eventTime) {
		this.eventTime = eventTime;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getUa() {
		return ua;
	}

	public void setUa(String ua) {
		this.ua = ua;
	}

	public String getOs() {
		return os;
	}

	public void setOs(String os) {
		this.os = os;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getClickid() {
		return clickid;
	}

	public void setClickid(String clickid) {
		this.clickid = clickid;
	}

	public String getCampaignId() {
		return campaignId;
	}

	public void setCampaignId(String campaignId) {
		this.campaignId = campaignId;
	}

	public String getAdgroupId() {
		return adgroupId;
	}

	public void setAdgroupId(String adgroupId) {
		this.adgroupId = adgroupId;
	}

	public String getCreativeId() {
		return creativeId;
	}

	public void setCreativeId(String creativeId) {
		this.creativeId = creativeId;
	}

	public String getAdvertiserId() {
		return advertiserId;
	}

	public void setAdvertiserId(String advertiserId) {
		this.advertiserId = advertiserId;
	}

	public String getIdfa() {
		return idfa;
	}

	public void setIdfa(String idfa) {
		this.idfa = idfa;
	}

	public String getIdfaMd5() {
		return idfaMd5;
	}

	public void setIdfaMd5(String idfaMd5) {
		this.idfaMd5 = idfaMd5;
	}

	public String getImei() {
		return imei;
	}

	public void setImei(String imei) {
		this.imei = imei;
	}

	public String getImeiMd5() {
		return imeiMd5;
	}

	public void setImeiMd5(String imeiMd5) {
		this.imeiMd5 = imeiMd5;
	}

	public String getOaid() {
		return oaid;
	}

	public void setOaid(String oaid) {
		this.oaid = oaid;
	}

	public String getOaidMd5() {
		return oaidMd5;
	}

	public void setOaidMd5(String oaidMd5) {
		this.oaidMd5 = oaidMd5;
	}

	public String getCaid() {
		return caid;
	}

	public void setCaid(String caid) {
		this.caid = caid;
	}

	public String getAndroidId() {
		return androidId;
	}

	public void setAndroidId(String androidId) {
		this.androidId = androidId;
	}

	public String getAndroidIdMd5() {
		return androidIdMd5;
	}

	public void setAndroidIdMd5(String androidIdMd5) {
		this.androidIdMd5 = androidIdMd5;
	}

	public String getAaid() {
		return aaid;
	}

	public void setAaid(String aaid) {
		this.aaid = aaid;
	}

	public String getGaid() {
		return gaid;
	}

	public void setGaid(String gaid) {
		this.gaid = gaid;
	}

	public String getIdfv() {
		return idfv;
	}

	public void setIdfv(String idfv) {
		this.idfv = idfv;
	}

}
