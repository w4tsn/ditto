/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.directives;

import static akka.http.javadsl.server.Directives.extractRequestContext;
import static org.eclipse.ditto.services.gateway.endpoints.directives.SecurityResponseHeadersDirective.createSecurityResponseHeaders;
import static org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils.enhanceLogWithCorrelationId;
import static org.eclipse.ditto.services.gateway.endpoints.utils.HttpUtils.getRawRequestUri;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.utils.tracing.MutableKamonTimer;
import org.eclipse.ditto.services.utils.tracing.TraceUtils;
import org.eclipse.ditto.services.utils.tracing.TracingTags;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.util.ByteString;

/**
 * Custom Akka Http directive which handles a request timeout.
 */
public final class RequestTimeoutHandlingDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestTimeoutHandlingDirective.class);
    private static final Duration SEARCH_WARN_TIMEOUT_MS = Duration.ofMillis(5_000);
    private static final Duration HTTP_WARN_TIMEOUT_MS = Duration.ofMillis(1_000);
    private static final String TRACING_ENTITY_SEARCH = "search/things";

    private RequestTimeoutHandlingDirective() {
        // no op
    }

    /**
     * Handles a request timeout.
     *
     * @param correlationId the correlationId which will be added to the log
     * @param inner the inner Route to wrap with the response headers
     * @return the new Route wrapping {@code inner} with the response headers
     */
    public static Route handleRequestTimeout(final String correlationId, final Supplier<Route> inner) {

        return Directives.extractActorSystem(actorSystem -> {
            final Config config = actorSystem.settings().config();

            return extractRequestContext(requestContext -> {

                final MutableKamonTimer mutableTimer = TraceUtils.createTimer(requestContext);

                final Supplier<Route> innerWithTimer = () -> {
                    mutableTimer.start();
                    LOGGER.debug("Started mutable timer <{}>", mutableTimer);
                    return Directives.mapResponse(response -> {
                        final int statusCode = response.status().intValue();
                        mutableTimer
                                .tag(TracingTags.STATUS_CODE, statusCode)
                                .stop();
                        checkDurationWarning(mutableTimer);
                        LOGGER.debug("Finished mutable timer <{}> with status <{}>", mutableTimer, statusCode);
                        return response;
                    }, inner);
                };

                return Directives.withRequestTimeoutResponse(
                        request ->
                                enhanceLogWithCorrelationId(correlationId, () ->
                                        doHandleRequestTimeout(correlationId, config, requestContext, mutableTimer)),
                        innerWithTimer);
            });
        });
    }

    private static void checkDurationWarning(final MutableKamonTimer mutableTimer) {
        final Duration duration = Duration.of(mutableTimer.getEndTimestamp() - mutableTimer.getStartTimestamp(),
                ChronoUnit.NANOS);
        final String entityType = mutableTimer.getTag(TracingTags.ENTITY_TYPE);
        if (TRACING_ENTITY_SEARCH.equals(entityType) && SEARCH_WARN_TIMEOUT_MS.minus(duration).isNegative()) {
            LOGGER.warn("Encountered slow search which took over {}ms: {}ms",
                    SEARCH_WARN_TIMEOUT_MS.toMillis(),
                    duration.toMillis());
        } else if (HTTP_WARN_TIMEOUT_MS.minus(duration).isNegative()) {
            LOGGER.warn("Encountered slow HTTP request which took over {}ms: {}ms",
                    HTTP_WARN_TIMEOUT_MS.toMillis(),
                    duration.toMillis());
        }
    }

    private static HttpResponse doHandleRequestTimeout(final String correlationId, final Config config,
            final RequestContext requestContext, final MutableKamonTimer mutableTimer) {
        final Duration duration = config.getDuration(ConfigKeys.AKKA_HTTP_SERVER_REQUEST_TIMEOUT);

        final DittoRuntimeException cre = GatewayServiceUnavailableException
                .newBuilder()
                .dittoHeaders(
                        DittoHeaders
                                .newBuilder()
                                .correlationId(correlationId)
                                .build())
                .build();

        final HttpRequest request = requestContext.getRequest();

        /* We have to log and create a trace here because the RequestResultLoggingDirective won't be called by akka
           in case of a timeout */
        final int statusCode = cre.getStatusCode().toInt();
        final String requestMethod = request.method().name();
        final String requestUri = request.getUri().toRelative().toString();
        LOGGER.warn("Request {} '{}' timed out after {}", requestMethod, requestUri, duration);
        LOGGER.info("StatusCode of request {} '{}' was: {}", requestMethod, requestUri, statusCode);
        final String rawRequestUri = getRawRequestUri(request);
        LOGGER.debug("Raw request URI was: {}", rawRequestUri);

        mutableTimer
                .tag(TracingTags.STATUS_CODE, statusCode)
                .stop();
        LOGGER.debug("Finished mutable timer <{}> after a request timeout with status <{}>", mutableTimer, statusCode);

        /* We have to add security response headers explicitly here because SecurityResponseHeadersDirective won't be
           called by akka in case of a timeout */
        final Iterable<HttpHeader> securityResponseHeaders = createSecurityResponseHeaders(config);
        return HttpResponse.create()
                .withStatus(statusCode)
                .withEntity(ContentTypes.APPLICATION_JSON, ByteString.fromString(cre.toJsonString()))
                .addHeaders(securityResponseHeaders);
    }

}
