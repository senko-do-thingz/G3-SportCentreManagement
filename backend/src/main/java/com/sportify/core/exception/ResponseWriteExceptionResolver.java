package com.sportify.core.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.springframework.web.util.DisconnectedClientHelper;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Terminal response failures must be resolved before controller advice tries to serialize an error. */
@Component
public final class ResponseWriteExceptionResolver extends AbstractHandlerExceptionResolver {
    private static final Logger LOG = LoggerFactory.getLogger(ResponseWriteExceptionResolver.class);
    private static final DisconnectedClientHelper DISCONNECT_LOG =
            new DisconnectedClientHelper(ResponseWriteExceptionResolver.class.getName());

    public ResponseWriteExceptionResolver() {
        setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception exception) {
        Evidence evidence = inspect(exception, ResponseWriteFailureFilter.failure(request));
        if (evidence.disconnected()) {
            // Spring emits a single DEBUG diagnostic, or the full cause chain at TRACE when requested.
            DISCONNECT_LOG.checkAndLogClientDisconnectedException(exception);
            return new ModelAndView();
        }
        if (response.isCommitted() || evidence.unusable()) {
            LOG.error("Response is no longer writable; no secondary error body will be sent", exception);
            if (!response.isCommitted()) response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return new ModelAndView();
        }
        return null; // Preserve all existing connected-client controller-advice status/body contracts.
    }

    private static Evidence inspect(Exception exception, IOException writeFailure) {
        if (writeFailure == null) return new Evidence(false, false);
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean correlated = false, containerAbort = false, excluded = false;
        Throwable root = exception;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (!seen.add(cause)) return new Evidence(false, true);
            correlated |= cause == writeFailure;
            // Only transport causes belonging to the observed write count, not application wrappers.
            containerAbort |= correlated && cause instanceof ClientAbortException;
            excluded |= cause instanceof DataAccessException || cause instanceof RestClientException;
            root = cause;
        }
        // A lifecycle exception type alone is not proof. Require the exact observed output failure,
        // and retain ERROR diagnostics for application wrappers even after a real output failure.
        boolean writeException = exception == writeFailure || exception instanceof AsyncRequestNotUsableException;
        boolean disconnected = correlated && writeException && !excluded && (containerAbort || root instanceof IOException
                && !(root instanceof AsyncRequestNotUsableException)
                && DisconnectedClientHelper.isClientDisconnectedException(root));
        return new Evidence(disconnected, true);
    }

    private record Evidence(boolean disconnected, boolean unusable) { }
}
