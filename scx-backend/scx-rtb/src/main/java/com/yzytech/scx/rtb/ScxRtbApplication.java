package com.yzytech.scx.rtb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = { "com.yzytech.scx.rtb", "com.yzytech.scx.ocpx.runtime" })
public class ScxRtbApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScxRtbApplication.class, args);
	}

}
