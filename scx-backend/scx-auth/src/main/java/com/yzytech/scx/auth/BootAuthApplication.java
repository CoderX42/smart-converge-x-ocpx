

package com.yzytech.scx.auth;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证授权模块
 *
 * @since 2022/2/18 14:45
 */
@EnableDubbo
@SpringBootApplication
public class BootAuthApplication {

	public static void main(String[] args) {
		SpringApplication.run(BootAuthApplication.class, args);
	}

}
