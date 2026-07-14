

package com.yzytech.scx.pay;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 支付模块启动类
 *
 * @since 2022/2/26 16:38
 */
@EnableDubbo
@SpringBootApplication
public class BootPayApplication {

	public static void main(String[] args) {
		SpringApplication.run(BootPayApplication.class, args);
	}

}
