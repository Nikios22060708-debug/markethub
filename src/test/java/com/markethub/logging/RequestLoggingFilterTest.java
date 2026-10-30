package com.markethub.logging;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void keepsSafeIncomingRequestId() {
        assertThat(RequestLoggingFilter.resolveRequestId("abc-123")).isEqualTo("abc-123");
    }

    @Test
    void replacesUnsafeOrMissingRequestId() {
        assertThat(RequestLoggingFilter.resolveRequestId("bad\nvalue")).hasSize(8).doesNotContain("\n");
        assertThat(RequestLoggingFilter.resolveRequestId(null)).hasSize(8);
    }

    @Test
    void addsRequestIdHeaderToResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("req-1");
    }

    @Test
    void logsErrorResponsesToo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/missing");
        request.setQueryString("a=1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void skipsPrometheusScrapes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNull();
    }
}
