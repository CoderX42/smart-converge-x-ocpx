package com.yzytech.scx.ocpx.manager.union.dto;

import com.yzytech.scx.ocpx.manager.BasicCallbackRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UnionCallbackReq extends BasicCallbackRequest {

	private Integer event_type;

	private Long event_time;

}
