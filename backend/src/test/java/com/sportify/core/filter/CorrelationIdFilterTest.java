package com.sportify.core.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_shouldEchoValidClientCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "client-cid-12345");

        AtomicReference<String> mdcDuringExecution = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            mdcDuringExecution.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        assertEquals("client-cid-12345", response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertEquals("client-cid-12345", request.getAttribute(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertEquals("client-cid-12345", mdcDuringExecution.get());
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY), "MDC must be cleared after filter completion");
    }

    @Test
    void doFilter_shouldGenerateUuidWhenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcDuringExecution = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            mdcDuringExecution.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        String generatedCid = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertNotNull(generatedCid);
        assertTrue(generatedCid.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
        assertEquals(generatedCid, request.getAttribute(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertEquals(generatedCid, mdcDuringExecution.get());
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void doFilter_shouldRegenerateWhenHeaderContainsInvalidCharacters() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "invalid id with spaces<script>");

        filter.doFilter(request, response, new MockFilterChain());

        String generatedCid = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertNotNull(generatedCid);
        assertNotEquals("invalid id with spaces<script>", generatedCid);
        assertTrue(generatedCid.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
    }

    @Test
    void doFilter_shouldRegenerateWhenHeaderExceedsMaxLength() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String overlyLongHeader = "a".repeat(65);
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, overlyLongHeader);

        filter.doFilter(request, response, new MockFilterChain());

        String generatedCid = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertNotNull(generatedCid);
        assertNotEquals(overlyLongHeader, generatedCid);
        assertTrue(generatedCid.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
    }

    @Test
    void doFilter_shouldAlwaysCleanMdcEvenWhenDownstreamThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-fail-test");

        FilterChain throwingChain = (req, res) -> {
            assertEquals("cid-fail-test", MDC.get(CorrelationIdFilter.MDC_KEY));
            throw new RuntimeException("Downstream simulated exception");
        };

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, throwingChain));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY), "MDC must be cleaned up in finally block upon error");
    }
}
