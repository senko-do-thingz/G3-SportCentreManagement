package com.sportify.core.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotWritableException;
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
        Evidence evidence = inspect(exception);
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

    private static Evidence inspect(Exception exception) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean async = false, containerAbort = false, excluded = false;
        Throwable root = exception;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (!seen.add(cause)) return new Evidence(false, exception instanceof AsyncRequestNotUsableException);
            async |= cause instanceof AsyncRequestNotUsableException;
            containerAbort |= cause instanceof ClientAbortException;
            excluded |= cause instanceof DataAccessException || cause instanceof RestClientException;
            root = cause;
        }
        // Spring 6.2 also recognizes the AsyncRequestNotUsableException name alone. Require transport
        // evidence instead, and never mistake a database or upstream HTTP failure for this client's abort.
        boolean disconnected = !excluded && (containerAbort || async && root instanceof IOException
                && !(root instanceof AsyncRequestNotUsableException)
                && DisconnectedClientHelper.isClientDisconnectedException(root));
        boolean unusable = exception instanceof AsyncRequestNotUsableException
                || !excluded && async && exception instanceof HttpMessageNotWritableException;
        return new Evidence(disconnected, unusable);
    }

    private record Evidence(boolean disconnected, boolean unusable) { }
}
