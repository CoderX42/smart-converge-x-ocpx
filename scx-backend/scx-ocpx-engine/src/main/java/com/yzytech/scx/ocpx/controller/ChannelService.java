package com.yzytech.scx.ocpx.controller;

import com.yzytech.scx.ocpx.bean.CallbackReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ChannelService {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	public abstract void callback(CallbackReq request);

}
