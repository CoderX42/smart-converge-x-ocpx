package com.yzytech.scx.ocpx;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * OCPX 数据面独立应用入口。
 *
 * <p>
 * 该进程只承载媒体 Feedback、广告主 Callback、配置内存索引和报表队列落库，不应被管理面 {@code scx-boot}
 * 扫描或装配。默认运行端口和上下文路径由本模块配置文件决定。
 */
@SpringBootApplication
@MapperScan({ "com.yzytech.scx.ocpx.configuration.mapper", "com.yzytech.scx.ocpx.report.mapper" })
@EnableAsync
@EnableScheduling
public class ScxOcpxEngineApplication {

	/**
	 * 启动 OCPX Engine。
	 * @param args Spring Boot 命令行参数
	 */
	public static void main(String[] args) {
		SpringApplication.run(ScxOcpxEngineApplication.class, args);
	}

}
