package com.yzytech.scx.ocpx.manager;

import com.yzytech.scx.ocpx.bean.FeedbackReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RequestService {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	protected final Logger errorLog = LoggerFactory.getLogger("error");

	public abstract void feedback(FeedbackReq request);

	public abstract boolean rta(FeedbackReq request);

}
