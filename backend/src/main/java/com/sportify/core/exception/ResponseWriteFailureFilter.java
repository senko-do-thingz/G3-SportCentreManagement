package com.sportify.core.exception;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Records concrete output failures, without inferring a disconnect from a controller's exception. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public final class ResponseWriteFailureFilter extends OncePerRequestFilter {
    private static final String FAILURE_ATTRIBUTE = ResponseWriteFailureFilter.class.getName() + ".failure";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            private ServletOutputStream output;

            @Override
            public ServletOutputStream getOutputStream() throws IOException {
                if (output == null) {
                    ServletOutputStream delegate;
                    try {
                        delegate = super.getOutputStream();
                    } catch (IOException failure) {
                        record(request, failure);
                        throw failure;
                    }
                    output = new ServletOutputStream() {
                        @Override public boolean isReady() { return delegate.isReady(); }
                        @Override public void setWriteListener(WriteListener listener) { delegate.setWriteListener(listener); }
                        @Override public void write(int value) throws IOException { observe(() -> delegate.write(value)); }
                        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                            observe(() -> delegate.write(bytes, offset, length));
                        }
                        @Override public void flush() throws IOException { observe(delegate::flush); }
                        @Override public void close() throws IOException { observe(delegate::close); }
                        private void observe(OutputOperation operation) throws IOException {
                            try {
                                operation.run();
                            } catch (IOException failure) {
                                record(request, failure);
                                throw failure;
                            }
                        }
                    };
                }
                return output;
            }
        });
    }

    static IOException failure(HttpServletRequest request) {
        Object attribute = request.getAttribute(FAILURE_ATTRIBUTE);
        return attribute instanceof WriteFailure failure ? failure.cause() : null;
    }

    private static void record(HttpServletRequest request, IOException cause) {
        if (failure(request) == null) request.setAttribute(FAILURE_ATTRIBUTE, new WriteFailure(cause));
    }

    private record WriteFailure(IOException cause) { }
    @FunctionalInterface
    private interface OutputOperation { void run() throws IOException; }
}
