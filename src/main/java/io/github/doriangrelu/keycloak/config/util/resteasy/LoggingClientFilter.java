package io.github.doriangrelu.keycloak.config.util.resteasy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingClientFilter implements ClientRequestFilter, ClientResponseFilter, WriterInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingClientFilter.class);
    private static final String BODY_STREAM_PROPERTY = "LoggingClientFilter.bodyStream";

    @Override
    public void filter(ClientRequestContext requestContext) {
        if (!logger.isDebugEnabled()) {
            return;
        }

        logger.debug(">> {} {}", requestContext.getMethod(), requestContext.getUri());
        logger.debug(">> Content-Type: {}", requestContext.getHeaderString("Content-Type"));

        // The body is not yet serialized at this point; it will be captured by aroundWriteTo
        if (!requestContext.hasEntity()) {
            logger.debug(">> (no body)");
        }
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, jakarta.ws.rs.WebApplicationException {
        if (!logger.isDebugEnabled()) {
            context.proceed();
            return;
        }

        // Replace the output stream to capture the serialized body
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        var original = context.getOutputStream();
        context.setOutputStream(capture);

        context.proceed();

        byte[] body = capture.toByteArray();
        String bodyStr = new String(body, StandardCharsets.UTF_8);
        if (bodyStr.length() > 2000) {
            logger.debug(">> Body ({} chars): {}...", bodyStr.length(), bodyStr.substring(0, 2000));
        } else {
            logger.debug(">> Body: {}", bodyStr);
        }

        // Write the captured body to the original stream
        original.write(body);
        original.flush();
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        if (!logger.isDebugEnabled()) {
            return;
        }

        logger.debug("<< {} {} -> {} {}",
                requestContext.getMethod(),
                requestContext.getUri(),
                responseContext.getStatus(),
                responseContext.getStatusInfo().getReasonPhrase());

        // Read and log response body, then reset the stream so it can be read again
        if (responseContext.hasEntity()) {
            try {
                InputStream entityStream = responseContext.getEntityStream();
                byte[] bytes = entityStream.readAllBytes();
                String responseBody = new String(bytes, StandardCharsets.UTF_8);

                if (responseBody.length() > 2000) {
                    logger.debug("<< Body ({} chars): {}...", responseBody.length(), responseBody.substring(0, 2000));
                } else {
                    logger.debug("<< Body: {}", responseBody);
                }

                // Reset stream for downstream consumers
                responseContext.setEntityStream(new ByteArrayInputStream(bytes));
            } catch (IOException e) {
                logger.debug("<< (unable to read response body: {})", e.getMessage());
            }
        }
    }
}
