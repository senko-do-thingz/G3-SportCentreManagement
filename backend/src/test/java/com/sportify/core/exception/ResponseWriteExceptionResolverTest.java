package com.sportify.core.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.io.IOException;
import java.net.SocketException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The same synchronous MVC/Jackson write path, with a deterministically failing servlet stream. */
class ResponseWriteExceptionResolverTest {
    private AnnotationConfigWebApplicationContext context;
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger global = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Logger transport = (Logger) LoggerFactory.getLogger("com.sportify.core.exception.ResponseWriteExceptionResolver");
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        logs.start();
        global.addAppender(logs);
        transport.addAppender(logs);
        previousLevel = transport.getLevel();
        transport.setLevel(Level.DEBUG);
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(ProbeConfiguration.class);
        context.refresh();
    }

    @AfterEach
    void close() {
        context.close();
        global.detachAppender(logs);
        transport.detachAppender(logs);
        transport.setLevel(previousLevel);
        logs.stop();
    }

    private MockMvc mvc(Probe probe) {
        return MockMvcBuilders.webAppContextSetup(context).addFilters((request, response, chain) -> {
            if (probe.committed) response.flushBuffer();
            chain.doFilter(request, new HttpServletResponseWrapper((HttpServletResponse) response) {
                @Override public void sendError(int status) { probe.sendErrors++; }
                @Override public void sendError(int status, String message) { probe.sendErrors++; }
                @Override public ServletOutputStream getOutputStream() throws IOException {
                    probe.streamAccesses++;
                    ServletOutputStream delegate = super.getOutputStream();
                    return new ServletOutputStream() {
                        @Override public boolean isReady() { return true; }
                        @Override public void setWriteListener(WriteListener listener) { }
                        @Override public void write(int value) throws IOException { delegate.write(value); }
                        @Override public void flush() throws IOException {
                            if (probe.flushFailure != null) throw probe.flushFailure;
                            delegate.flush();
                        }
                    };
                }
            });
        }).build();
    }

    @Test
    void clientAbortDuringJsonFlushDoesNotLogUnhandledOrAttemptAnotherBody() throws Exception {
        Probe probe = new Probe(new ClientAbortException(new IOException(
                "An established connection was aborted by the software in your host machine")), false);
        var result = mvc(probe).perform(get("/probe/data")).andReturn();
        assertInstanceOf(AsyncRequestNotUsableException.class, result.getResolvedException());
        assertEquals(1, context.getBean(BodyCounter.class).bodies, "No secondary error serialization");
        assertEquals(0, errors());
        assertEquals(1, logs.list.stream().filter(e -> e.getLevel() == Level.DEBUG).count());
        assertEquals(0, probe.sendErrors);
    }

    @Test
    void committedClientAbortDoesNotAttemptASecondaryBody() throws Exception {
        Probe probe = new Probe(new ClientAbortException("Synthetic disconnect"), true);
        mvc(probe).perform(get("/probe/data"));
        assertEquals(1, context.getBean(BodyCounter.class).bodies);
        assertEquals(0, errors());
        assertEquals(0, probe.sendErrors);
    }

    @Test
    void brokenPipeDuringServletFlushIsRecognizedButNotAConnectedServerRead() throws Exception {
        mvc(new Probe(new SocketException("Broken pipe"), false)).perform(get("/probe/data"));
        assertEquals(1, context.getBean(BodyCounter.class).bodies);
        assertEquals(0, errors());
        assertEquals(1, logs.list.stream().filter(e -> e.getLevel() == Level.DEBUG).count());
    }

    @Test
    void nestedContainerAbortIsHandledBeforeTheCatchAllAdvice() throws Exception {
        Probe probe = new Probe(null, false);
        var result = mvc(probe).perform(get("/probe/error").param("kind", "nested-abort")).andReturn();
        assertEquals("", result.getResponse().getContentAsString());
        assertEquals(0, context.getBean(BodyCounter.class).bodies);
        assertEquals(0, probe.streamAccesses);
        assertEquals(0, errors());
    }

    @Test
    void unexpectedStreamFailureIsAnErrorButNeverWritesAnotherBody() throws Exception {
        mvc(new Probe(new IOException("Synthetic output device failure"), false)).perform(get("/probe/data"));
        assertEquals(1, context.getBean(BodyCounter.class).bodies);
        assertEquals(1, errors());
        assertEquals(0, logs.list.stream().filter(e -> e.getLevel() == Level.DEBUG).count());
    }

    @Test
    void unexplainedAsyncLifecycleFailureIsNotClassifiedAsDisconnectOrSuccess() throws Exception {
        mvc(new Probe(null, false)).perform(get("/probe/error").param("kind", "unusable"))
                .andExpect(status().isInternalServerError());
        assertEquals(0, context.getBean(BodyCounter.class).bodies);
        assertEquals(1, errors());
    }

    @Test
    void committedServerFailureIsObservableWithoutAppendingJson() throws Exception {
        Probe probe = new Probe(null, true);
        mvc(probe).perform(get("/probe/error").param("kind", "server"));
        assertEquals(0, context.getBean(BodyCounter.class).bodies);
        assertEquals(0, probe.streamAccesses);
        assertEquals(0, probe.sendErrors);
        assertEquals(1, errors());
    }

    @Test
    void connectedServerIoDatabaseAndUpstreamFailuresKeepTheirErrorEnvelope() throws Exception {
        for (String kind : new String[]{"server", "io", "socket", "database", "database-abort", "upstream"}) {
            mvc(new Probe(null, false)).perform(get("/probe/error").param("kind", kind))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("Internal server error"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
        assertEquals(6, errors());
    }

    @Test
    void actualJacksonGetterFailureIsNotAClientDisconnect() throws Exception {
        mvc(new Probe(null, false)).perform(get("/probe/serialization"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
        assertEquals(1, errors());
        assertEquals(0, logs.list.stream().filter(e -> e.getLevel() == Level.DEBUG).count());
    }

    private long errors() { return logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).count(); }
    private static class Probe {
        final IOException flushFailure;
        final boolean committed;
        int streamAccesses;
        int sendErrors;
        Probe(IOException failure, boolean committed) { this.flushFailure = failure; this.committed = committed; }
    }

    @Configuration
    @EnableWebMvc
    @ComponentScan(basePackageClasses = GlobalExceptionHandler.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = {GlobalExceptionHandler.class, HandlerExceptionResolver.class}))
    static class ProbeConfiguration {
        @Bean ProbeController probeController() { return new ProbeController(); }
        @Bean BodyCounter bodyCounter() { return new BodyCounter(); }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe/data") Map<String, String> data() { return Map.of("fixture", "synthetic"); }
        @GetMapping("/probe/error") Object error(@org.springframework.web.bind.annotation.RequestParam String kind) throws Exception {
            throw switch (kind) {
                case "nested-abort" -> new IllegalStateException("Synthetic wrapper", new ClientAbortException("Synthetic abort"));
                case "unusable" -> new AsyncRequestNotUsableException("Synthetic lifecycle failure");
                case "io" -> new IOException("Synthetic input read failure");
                case "socket" -> new SocketException("Broken pipe while reading an upstream fixture");
                case "database" -> new DataAccessResourceFailureException("Synthetic database failure", new SocketException("Connection reset by peer"));
                case "database-abort" -> new DataAccessResourceFailureException("Synthetic database failure", new ClientAbortException("Synthetic nested transport type"));
                case "upstream" -> new RestClientException("Synthetic upstream failure", new SocketException("Connection reset by peer"));
                default -> new IllegalStateException("Synthetic internal detail");
            };
        }
        @GetMapping("/probe/serialization") BrokenValue serialization() { return new BrokenValue(); }
    }
    static class BrokenValue {
        public String getValue() { throw new IllegalStateException("Synthetic serializer failure"); }
    }
    @RestControllerAdvice
    static class BodyCounter implements ResponseBodyAdvice<Object> {
        int bodies;
        @Override public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) { return true; }
        @Override public Object beforeBodyWrite(Object body, MethodParameter type, MediaType media,
                Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
            bodies++;
            return body;
        }
    }
}
