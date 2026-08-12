package com.yzytech.scx.ocpx.manager;

import com.yzytech.scx.ocpx.bean.FeedbackReq;
import com.yzytech.scx.ocpx.manager.union.UnionServiceImpl;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class RequestServiceManager {

	public static final ExecutorService EXECUTORS = new ThreadPoolExecutor(500, 500, 30L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());

	private static UnionServiceImpl unionService;

	public RequestServiceManager(UnionServiceImpl unionService) {
		RequestServiceManager.unionService = unionService;
	}

	public static void feedback(FeedbackReq request) {
		EXECUTORS.execute(() -> {
			try {
				if (unionService != null) {
					unionService.feedback(request);
				}
			}
			catch (Exception ignored) {
			}
		});
	}

	public static boolean rta(FeedbackReq request) {
		try {
			return unionService != null && unionService.rta(request);
		}
		catch (Exception exception) {
			exception.printStackTrace();
			return false;
		}
	}

}
