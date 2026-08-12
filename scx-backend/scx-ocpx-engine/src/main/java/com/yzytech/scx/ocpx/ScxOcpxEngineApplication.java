package com.yzytech.scx.ocpx;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.yzytech.scx.ocpx.dao.mapper")
@EnableAsync
@EnableScheduling
public class ScxOcpxEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScxOcpxEngineApplication.class, args);
	}

}
