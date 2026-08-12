package com.yzytech.scx.ocpx.support;

import com.yzytech.scx.ocpx.index.IndexDataLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemMemory implements CommandLineRunner {

	private final IndexDataLoader indexDataLoader;

	@Override
	public void run(String... args) {
		init();
	}

	public void init() {
		log.info("SystemMemory load");
		indexDataLoader.init();
		log.info("SystemMemory load end");
	}

}
