

package com.yzytech.scx.common.myabtis.shop;

import com.yzytech.scx.common.core.constant.CommonConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

/**
 * @description
 * @date 2025/8/5
 */
@Slf4j
@Component
public class BootShopContextFilter extends GenericFilterBean {

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		// 获取请求头中的 shopId
		String shopId = request.getHeader(CommonConstants.SHOP_ID);
		if (StringUtils.hasText(shopId)) {
			ShopContextHolder.setShopId(shopId);
		}
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			ShopContextHolder.clear();
		}
	}

}
