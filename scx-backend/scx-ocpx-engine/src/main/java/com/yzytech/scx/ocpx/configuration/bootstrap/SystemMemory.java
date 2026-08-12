package com.yzytech.scx.ocpx.configuration.bootstrap;

import com.yzytech.scx.ocpx.configuration.index.IndexDataLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 四实体内存索引的启动初始化器。
 *
 * <p>
 * 应用接受流量前从数据库装载广告主、推广链接、媒体和监测任务配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemMemory implements CommandLineRunner {

	private final IndexDataLoader indexDataLoader;

	@Override
	public void run(String... args) {
		init();
	}

	/** 重新装载全部 OCPX 配置索引，也供配置刷新任务调用。 */
	public void init() {
		log.info("SystemMemory load");
		indexDataLoader.init();
		log.info("SystemMemory load end");
	}

}
