

package com.yzytech.scx.common.myabtis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yzytech.scx.common.myabtis.handler.MyMetaObjectHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mybatis plus 统一配置
 *
 * @since 2022/2/15 15:48
 */
@Configuration
public class MybatisPlusConfig {

	/**
	 * 自动填充
	 * @return MyMetaObjectHandler
	 */
	@Bean
	public MyMetaObjectHandler metaObjectHandler() {
		return new MyMetaObjectHandler();
	}

	/**
	 * 使用多个功能需要注意顺序关系,建议使用如下顺序
	 *
	 * <p>
	 * 分页、乐观锁和 SQL 安全防护按顺序注册。
	 *
	 * @since 2022/2/16 15:24
	 */
	@Bean
	public MybatisPlusInterceptor mybatisPlusInterceptor() {
		MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
		// 分页插件
		interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
		// 乐观锁
		interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
		// 防止全表更新与删除
		interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

		return interceptor;
	}

}
