package com.yzytech.scx.ocpx.audit.web;

import com.yzytech.scx.ocpx.audit.service.WireAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Slf4j
public class WireAuditCaptureFilter extends OncePerRequestFilter {

	private final WireAuditService auditService;

	public WireAuditCaptureFilter(WireAuditService auditService) {
		this.auditService = auditService;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !auditService.enabled() || path(request).startsWith("/actuator");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
		Classification classification = classify(path(request));
		Map<String, Object> rawRequest = rawRequest(request, new byte[0]);
		CachedBodyRequestWrapper requestWrapper = null;
		WireAuditService.Handle handle = null;
		Throwable failure = null;
		try {
			requestWrapper = new CachedBodyRequestWrapper(request);
			rawRequest.put("bodyBase64", Base64.getEncoder().encodeToString(requestWrapper.body()));
			try {
				handle = auditService.beginInbound(classification.stage(), classification.protocol(),
						classification.trafficClass(), rawRequest, route(request));
			}
			catch (RuntimeException auditFailure) {
				log.error("OCPX_AUDIT_INBOUND_START_FAILED path:{}", path(request), auditFailure);
			}
			filterChain.doFilter(requestWrapper, responseWrapper);
		}
		catch (IOException | ServletException | RuntimeException exception) {
			failure = exception;
			throw exception;
		}
		finally {
			if (handle == null) {
				try {
					handle = auditService.beginInbound(classification.stage(), classification.protocol(),
							classification.trafficClass(), rawRequest, route(request));
				}
				catch (RuntimeException auditFailure) {
					log.error("OCPX_AUDIT_INBOUND_RECOVERY_START_FAILED path:{}", path(request), auditFailure);
				}
			}
			Map<String, List<String>> responseHeaders = responseHeaders(responseWrapper);
			Integer observedStatus = failure != null && responseWrapper.getStatus() < 400 ? null
					: responseWrapper.getStatus();
			try {
				auditService.completeHttp(handle, observedStatus, responseHeaders,
						responseWrapper.getContentAsByteArray(), conclusion(responseWrapper.getStatus(), failure), failure,
						(WireAuditService.Route) request.getAttribute(WireAuditRequestContext.ROUTE_ATTRIBUTE));
			}
			catch (RuntimeException auditFailure) {
				log.error("OCPX_AUDIT_INBOUND_COMPLETION_FAILED path:{}", path(request), auditFailure);
			}
			finally {
				responseWrapper.copyBodyToResponse();
			}
		}
	}

	private Map<String, Object> rawRequest(HttpServletRequest request, byte[] body) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("method", request.getMethod());
		value.put("scheme", request.getScheme());
		value.put("authority", request.getHeader("host"));
		value.put("path", path(request));
		value.put("rawQuery", request.getQueryString());
		value.put("headers", requestHeaders(request));
		value.put("bodyBase64", Base64.getEncoder().encodeToString(body));
		value.put("remoteAddress", request.getRemoteAddr());
		return value;
	}

	private Map<String, List<String>> requestHeaders(HttpServletRequest request) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		Enumeration<String> names = request.getHeaderNames();
		if (names == null)
			return result;
		while (names.hasMoreElements()) {
			String name = names.nextElement();
			result.put(name, java.util.Collections.list(request.getHeaders(name)));
		}
		return result;
	}

	private Map<String, List<String>> responseHeaders(HttpServletResponse response) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		for (String name : response.getHeaderNames()) {
			result.put(name, new ArrayList<>(response.getHeaders(name)));
		}
		return result;
	}

	private WireAuditService.Route route(HttpServletRequest request) {
		return new WireAuditService.Route(null, parameter(request, "request_id", "requestId"),
				parameter(request, "aId"), parameter(request, "lId"), parameter(request, "mId"),
				parameter(request, "tId"));
	}

	private String parameter(HttpServletRequest request, String... names) {
		for (String name : names) {
			if (StringUtils.hasText(request.getParameter(name)))
				return request.getParameter(name);
		}
		return null;
	}

	private Classification classify(String path) {
		if (path.endsWith("/feedback"))
			return new Classification("MEDIA_INGRESS", "MEDIA", "FORMAL");
		if (path.endsWith("/aerim/callback"))
			return new Classification("ADVERTISER_CALLBACK", "AERIM", "FORMAL");
		if (path.endsWith("/weifeng/callback"))
			return new Classification("ADVERTISER_CALLBACK", "WEIFENG", "FORMAL");
		if (path.endsWith("/gravity/callback"))
			return new Classification("ADVERTISER_CALLBACK", "GRAVITY", "FORMAL");
		if (path.endsWith("/test-callback"))
			return new Classification("MEDIA_DELIVERY", "TEST_COLLECTOR", "TEST");
		if (path.endsWith("/callback"))
			return new Classification("ADVERTISER_CALLBACK", "TEMPLATE_GET", "FORMAL");
		return new Classification("MEDIA_INGRESS", "UNKNOWN", "FORMAL");
	}

	private String conclusion(int status, Throwable failure) {
		if (failure != null)
			return failure.getClass().getSimpleName();
		return status >= 200 && status < 400 ? "HTTP_ACCEPTED" : "HTTP_" + status;
	}

	private String path(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String context = request.getContextPath();
		return StringUtils.hasText(context) && uri.startsWith(context) ? uri.substring(context.length()) : uri;
	}

	private record Classification(String stage, String protocol, String trafficClass) {
	}

	private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

		private final byte[] body;

		private CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
			super(request);
			this.body = request.getInputStream().readAllBytes();
		}

		private byte[] body() {
			return body;
		}

		@Override
		public ServletInputStream getInputStream() {
			ByteArrayInputStream input = new ByteArrayInputStream(body);
			return new ServletInputStream() {
				@Override
				public boolean isFinished() {
					return input.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener readListener) {
					if (readListener == null)
						throw new IllegalArgumentException("readListener is required");
					try {
						if (isFinished())
							readListener.onAllDataRead();
						else
							readListener.onDataAvailable();
					}
					catch (IOException exception) {
						readListener.onError(exception);
					}
				}

				@Override
				public int read() {
					return input.read();
				}
			};
		}

		@Override
		public BufferedReader getReader() {
			String encoding = getCharacterEncoding();
			return new BufferedReader(new InputStreamReader(getInputStream(),
					encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
		}

	}

}
