package com.yzytech.scx.ocpx.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "com.yzytech.scx.ocpx.engine", "com.yzytech.scx.ocpx.runtime" })
public class ScxOcpxEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScxOcpxEngineApplication.class, args);
	}

}
