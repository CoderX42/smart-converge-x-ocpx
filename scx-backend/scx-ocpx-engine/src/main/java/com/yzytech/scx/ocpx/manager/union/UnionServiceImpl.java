package com.yzytech.scx.ocpx.manager.union;

import com.yzytech.scx.ocpx.bean.FeedbackReq;
import com.yzytech.scx.ocpx.constant.enums.EventTypeEnum;
import com.yzytech.scx.ocpx.entity.OcpxAdvertiser;
import com.yzytech.scx.ocpx.index.UserDspIndex;
import com.yzytech.scx.ocpx.manager.RequestService;
import com.yzytech.scx.ocpx.util.NetUtil;
import org.springframework.stereotype.Component;

@Component
public class UnionServiceImpl extends RequestService {

	private final UnionParamAdapter unionParamAdapter;

	private final UserDspIndex userDspIndex;

	public UnionServiceImpl(UnionParamAdapter unionParamAdapter, UserDspIndex userDspIndex) {
		this.unionParamAdapter = unionParamAdapter;
		this.userDspIndex = userDspIndex;
	}

	@Override
	public void feedback(FeedbackReq feedback) {
		try {
			if (!EventTypeEnum.CLICK.equals(feedback.getEvent())) {
				return;
			}
			OcpxAdvertiser advertiser = userDspIndex.get(feedback.getDspid());
			String requestUrl = unionParamAdapter.getReqUrl(advertiser, feedback);
			if (requestUrl == null) {
				return;
			}
			log.info("union linkId:{} req:{}", feedback.getLinkid(), requestUrl);
			String content = NetUtil.httpGet(requestUrl, null);
			log.info("union linkId:{}, content:{}", feedback.getLinkid(), content);
		}
		catch (Exception exception) {
			errorLog.error("执行 [union：{}] 异常 message:{}", feedback.getLinkid(), exception.getMessage(), exception);
			throw new RuntimeException(exception);
		}
	}

	@Override
	public boolean rta(FeedbackReq request) {
		return false;
	}

}
