package com.vocenocoracao.supermercado_api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.clear();
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
        MDC.clear();
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    @Test
    void generatesARequestIdExposesItInTheResponseAndInTheLogContext() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> seenInsideTheChain = new ArrayList<>();
        FilterChain chain = (servletRequest, servletResponse) -> seenInsideTheChain.add(MDC.get("requestId"));

        filter.doFilter(request("GET", "/api/products"), response, chain);

        String header = response.getHeader("X-Request-Id");
        assertThat(UUID.fromString(header)).isNotNull();
        assertThat(seenInsideTheChain).containsExactly(header);
    }

    @Test
    void reusesAValidRequestIdSentByTheCaller() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/products");
        request.addHeader("X-Request-Id", "frontend-123.abc_9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("frontend-123.abc_9");
    }

    @Test
    void replacesAnUnsafeRequestIdInsteadOfWritingItToTheLogs() throws Exception {
        for (String unsafe : List.of("with space", "line\nbreak", "x".repeat(65), "<script>", "")) {
            MockHttpServletRequest request = request("GET", "/api/products");
            request.addHeader("X-Request-Id", unsafe);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getHeader("X-Request-Id")).as(unsafe).isNotEqualTo(unsafe);
            assertThat(UUID.fromString(response.getHeader("X-Request-Id"))).isNotNull();
        }
    }

    @Test
    void logsOneLineWithMethodPathStatusAndTheRequestIdInTheContext() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> ((MockHttpServletResponse) servletResponse).setStatus(404);

        filter.doFilter(request("GET", "/api/products/abc"), response, chain);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).startsWith("GET /api/products/abc -> 404 (").endsWith(" ms)");
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", response.getHeader("X-Request-Id"));
    }

    @Test
    void doesNotFloodTheLogsWithHealthChecks() throws Exception {
        filter.doFilter(request("GET", "/actuator/health/readiness"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(appender.list).isEmpty();
    }

    @Test
    void healthChecksAreStillLoggedWhenDebugIsEnabled() throws Exception {
        logger.setLevel(Level.DEBUG);

        filter.doFilter(request("GET", "/actuator/health"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.DEBUG);
    }

    @Test
    void clearsTheLogContextAfterTheRequest() throws Exception {
        filter.doFilter(request("GET", "/api/products"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void clearsTheLogContextAndStillLogsWhenTheChainFails() {
        FilterChain failing = (servletRequest, servletResponse) -> {
            throw new IllegalStateException("falha");
        };

        assertThatThrownBy(() -> filter.doFilter(request("GET", "/api/orders"), new MockHttpServletResponse(), failing))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get("requestId")).isNull();
        assertThat(appender.list).hasSize(1);
    }
}
