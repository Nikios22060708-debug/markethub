package com.markethub.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Логирует каждый HTTP-запрос: метод, путь, статус, длительность.
 * Каждому запросу присваивается requestId (MDC + заголовок X-Request-Id),
 * поэтому все строки лога одного запроса легко найти вместе.
 * Стоит первым в цепочке, чтобы в лог попадали и ответы 401/403 от Spring Security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
            int status = response.getStatus();
            if (status >= 500) {
                log.error("{} {}{} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), query, status, durationMs);
            } else if (status >= 400) {
                log.warn("{} {}{} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), query, status, durationMs);
            } else {
                log.info("{} {}{} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), query, status, durationMs);
            }
            MDC.remove(MDC_KEY);
        }
    }

    /** Не логируем частый опрос метрик Prometheus, чтобы не засорять лог. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/prometheus");
    }

    /** Принимаем requestId клиента только безопасного формата (защита от подделки строк лога). */
    static String resolveRequestId(String incoming) {
        if (incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
