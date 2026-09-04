package com.yzytech.scx.ocpx.rta.adapter;

import com.yzytech.scx.ocpx.rta.model.RtaAdapterResult;
import com.yzytech.scx.ocpx.rta.model.RtaDecisionRequest;
import com.yzytech.scx.ocpx.rta.model.RtaProfileSnapshot;

import java.util.List;



/** 可插拔 RTA 决策协议；本期只启用 Mock 实现。 */
public interface RtaDecisionAdapter {

	String code();

	RtaAdapterResult decide(RtaDecisionRequest request, RtaProfileSnapshot profile, List<String> taskIds);

}
