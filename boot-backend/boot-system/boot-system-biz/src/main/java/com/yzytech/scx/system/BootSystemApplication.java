

package com.yzytech.scx.system;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 系统管理模块
 *
 * @since 2022/2/26 16:51
 */
@EnableDubbo
@SpringBootApplication
public class BootSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(BootSystemApplication.class, args);
	}

}
