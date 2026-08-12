package com.yzytech.scx.ocpx.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.TreeMap;

/**
 * 下游媒体 v2 回传。四实体关系只允许由 monitor_task_id 对应的启用任务派生。
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class FeedbackReqV2 {

	@JsonProperty("schema_version")
	private String schemaVersion = "2.0";

	@NotBlank
	@JsonProperty("monitor_task_id")
	private String monitorTaskId;

	@NotBlank
	private String event;

	@NotBlank
	@JsonProperty("event_id")
	private String eventId;

	@JsonProperty("click_id")
	private String clickId;

	@NotNull
	@JsonProperty("event_time")
	private Long eventTime;

	private Map<String, Object> device = new TreeMap<>();

	private Map<String, Object> extensions = new TreeMap<>();

	public Map<String, Object> extensions() {
		return extensions;
	}

	public String getSchemaVersion() {
		return schemaVersion;
	}

	public void setSchemaVersion(String schemaVersion) {
		this.schemaVersion = schemaVersion;
	}

	public String getMonitorTaskId() {
		return monitorTaskId;
	}

	public void setMonitorTaskId(String monitorTaskId) {
		this.monitorTaskId = monitorTaskId;
	}

	public String getEvent() {
		return event;
	}

	public void setEvent(String event) {
		this.event = event;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getClickId() {
		return clickId;
	}

	public void setClickId(String clickId) {
		this.clickId = clickId;
	}

	public Long getEventTime() {
		return eventTime;
	}

	public void setEventTime(Long eventTime) {
		this.eventTime = eventTime;
	}

	public Map<String, Object> getDevice() {
		return device;
	}

	public void setDevice(Map<String, Object> device) {
		this.device = device == null ? new TreeMap<>() : new TreeMap<>(device);
	}

	public Map<String, Object> getExtensions() {
		return extensions;
	}

	public void setExtensions(Map<String, Object> extensions) {
		this.extensions = extensions == null ? new TreeMap<>() : new TreeMap<>(extensions);
	}

}
