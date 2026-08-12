package com.yzytech.scx.ocpx.controller;

import com.yzytech.scx.ocpx.bean.CallbackReq;
import com.yzytech.scx.ocpx.controller.general.GeneralServiceImpl;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class ChannelServiceManager {

	public static final ExecutorService EXECUTORS = new ThreadPoolExecutor(500, 500, 30L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());

	private static GeneralServiceImpl generalService;

	public ChannelServiceManager(GeneralServiceImpl generalService) {
		ChannelServiceManager.generalService = generalService;
	}

	public static void callback(CallbackReq request) {
		EXECUTORS.execute(() -> {
			try {
				if (generalService != null) {
					generalService.callback(request);
				}
			}
			catch (Exception ignored) {
			}
		});
	}

}
