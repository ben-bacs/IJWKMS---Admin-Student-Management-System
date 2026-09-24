package edu.wvsu.ijwkms.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTests {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesSafeClientCorrelationIdAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request, response, (ignoredRequest, ignoredResponse) -> assertThat(MDC.get(CorrelationIdFilter.MDC_KEY))
                        .isEqualTo("request-123"));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("request-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "unsafe value\r\nheader");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
