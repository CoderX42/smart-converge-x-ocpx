

package com.yzytech.scx.common.myabtis.datascope;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.yzytech.scx.common.myabtis.shop.ShopContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * @description
 * @date 2025/8/5
 */
@Slf4j
public class UserShopDataPermissionInterceptor implements InnerInterceptor {

	@Override
	public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
			ResultHandler resultHandler, BoundSql boundSql) {

		PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);
		String originalSql = boundSql.getSql();

		if (!hasShopDataScopeAnnotation(ms)) {
			return;
		}

		// 从上下文（ThreadLocal）中获取 shopId
		String shopId = ShopContextHolder.getShopId(); // 你需要封装 ThreadLocal 工具类
		if (!StringUtils.hasText(shopId)) {
			return;
		}

		// 构造 shopId 条件 SQL
		if (ms.getId().contains("selectCount")) {
			originalSql = String.format("%s %s shop_id = '%s'", originalSql,
					StrUtil.containsAnyIgnoreCase(originalSql, "where") ? "AND" : "WHERE", shopId);
		}
		else {
			originalSql = String.format("SELECT * FROM (%s) temp_data_scope WHERE temp_data_scope.shop_id = '%s'",
					originalSql, shopId);
		}

		mpBoundSql.sql(originalSql);
	}

	private boolean hasShopDataScopeAnnotation(MappedStatement ms) {
		String id = ms.getId();
		int lastDot = id.lastIndexOf(".");
		String className = id.substring(0, lastDot);
		String methodName = id.substring(lastDot + 1);

		try {
			Class<?> mapperClass = Class.forName(className);
			Method[] methods = mapperClass.getDeclaredMethods();

			for (Method method : methods) {
				if (method.getName().equals(methodName) && method.isAnnotationPresent(UserShopDataScope.class)) {
					return true;
				}
			}
		}
		catch (Exception e) {
			log.warn("用户端数据权限注解判断失败", e);
		}
		return false;
	}

}
