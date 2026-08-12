package com.yzytech.scx.ocpx.bean;

import com.yzytech.scx.ocpx.constant.enums.ActionTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackReq {

	private FeedbackReq feedback;

	private ActionTypeEnum actionType;

	private Long actionTime;

}
