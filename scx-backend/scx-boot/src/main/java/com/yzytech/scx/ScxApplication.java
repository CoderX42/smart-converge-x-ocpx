
package com.yzytech.scx;

import com.yzytech.scx.common.job.annotation.BootEnableXxlJob;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 单体模块启动类
 *
 * @since 2022/2/18 13:52
 */
@SpringBootApplication
@EnableScheduling
@BootEnableXxlJob
public class ScxApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScxApplication.class, args);
	}

}
