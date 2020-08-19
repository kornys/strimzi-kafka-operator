/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest.apiclients;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static java.net.HttpURLConnection.HTTP_OK;

public abstract class AbstractApiClient implements AutoCloseable {
    private static final Logger log = LogManager.getLogger(AbstractApiClient.class);

    protected final Vertx vertx;
    protected final String host;
    protected final int port;

    private WebClient client;

    protected abstract String apiClientName();

    protected AbstractApiClient(String host, int port) {
        VertxOptions options = new VertxOptions()
                .setWorkerPoolSize(1)
                .setInternalBlockingPoolSize(1)
                .setEventLoopPoolSize(1);
        this.vertx = Vertx.vertx(options);
        this.host = host;
        this.port = port;
    }

    protected void reconnect() {
        closeClient();
        getClient();
    }

    /**
     * Get the client, create a new one if necessary.
     *
     * @return The client to use.
     */
    protected WebClient getClient() {
        if (this.client == null) {
            this.client = createClient();
        }
        return this.client;
    }

    @Override
    public void close() {
        closeClient();
        this.vertx.close();
    }

    private void closeClient() {
        if (client != null) {
            this.client.close();
            this.client = null;
        }
    }

    protected WebClient createClient() {
        return WebClient.create(vertx, new WebClientOptions()
                .setSsl(true)
                .setTrustAll(true)
                .setVerifyHost(false));
    }

    protected <T> void responseHandler(AsyncResult<HttpResponse<T>> ar, CompletableFuture<T> promise, int expectedCode,
            String warnMessage) {
        responseHandler(ar, promise, expectedCode, warnMessage, true);
    }

    protected <T> void responseHandler(AsyncResult<HttpResponse<T>> ar, CompletableFuture<T> promise, int expectedCode,
            String warnMessage, boolean throwException) {
        responseHandler(ar, promise, responseCode -> responseCode == expectedCode, String.valueOf(expectedCode), warnMessage, throwException);
    }

    protected <T> void responseHandler(AsyncResult<HttpResponse<T>> ar, CompletableFuture<T> promise, Predicate<Integer> expectedCodePredicate,
            String warnMessage, boolean throwException) {

        responseHandler(ar, promise, expectedCodePredicate, expectedCodePredicate.toString(), warnMessage, throwException);

    }

    protected <T> void responseHandler(AsyncResult<HttpResponse<T>> ar, CompletableFuture<T> promise, Predicate<Integer> expectedCodePredicate,
            String expectedCodeOrCodes, String warnMessage, boolean throwException) {
        try {
            if (ar.succeeded()) {
                HttpResponse<T> response = ar.result();
                T body = response.body();
                if (expectedCodePredicate.negate().test(response.statusCode())) {
                    log.error("expected-code: {}, response-code: {}, body: {}, op: {}", expectedCodeOrCodes, response.statusCode(), response.body(), warnMessage);
                    promise.completeExceptionally(new RuntimeException("Status " + response.statusCode() + " body: " + (body != null ? body.toString() : null)));
                } else if (response.statusCode() < HTTP_OK || response.statusCode() >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    if (throwException) {
                        promise.completeExceptionally(new RuntimeException(body == null ? "null" : body.toString()));
                    } else {
                        promise.complete(ar.result().body());
                    }
                } else {
                    promise.complete(ar.result().body());
                }
            } else {
                log.warn("Request failed: {}", warnMessage, ar.cause());
                promise.completeExceptionally(ar.cause());
            }
        } catch (io.vertx.core.json.DecodeException decEx) {
            if (ar.result().bodyAsString().toLowerCase(Locale.ENGLISH).contains("application is not available")) {
                log.warn("'{}' is not available.", apiClientName(), ar.cause());
                throw new IllegalStateException(String.format("'%s' is not available.", apiClientName()));
            } else {
                log.warn("Unexpected object received", ar.cause());
                throw new IllegalStateException("JsonObject expected, but following object was received: " + ar.result().bodyAsString());
            }
        }
    }

}
