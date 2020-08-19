/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest.apiclients;

import io.strimzi.test.k8s.KubeClusterResource;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PrometheusApiClient extends AbstractApiClient {

    private final String token;

    public PrometheusApiClient(String host, int port) {
        super(host, port);
        this.token = KubeClusterResource.getInstance().cluster().CONFIG.getOauthToken();
    }

    @Override
    protected String apiClientName() {
        return "prometheus-client";
    }

    @Override
    protected WebClient createClient() {
        return WebClient.create(vertx, new WebClientOptions()
                .setSsl(true)
                .setTrustAll(true)
                .setVerifyHost(false));
    }

    public JsonObject getRules() throws Exception {
        CompletableFuture<JsonObject> responsePromise = new CompletableFuture<>();
        getClient().get(port, host, "/api/v1/rules")
                .bearerTokenAuthentication(token)
                .as(BodyCodec.jsonObject())
                .timeout(120000)
                .send(ar -> responseHandler(ar, responsePromise, HttpURLConnection.HTTP_OK, "Error getting prometheus rules"));
        return responsePromise.get(150000, TimeUnit.SECONDS);
    }

    public JsonObject doQuery(String query) throws Exception {
        Objects.requireNonNull(query);
        CompletableFuture<JsonObject> responsePromise = new CompletableFuture<>();
        String uri = String.format("/api/v1/query?query=%s", query);
        getClient().get(port, host, uri)
                .bearerTokenAuthentication(token)
                .as(BodyCodec.jsonObject())
                .timeout(120000)
                .send(ar -> responseHandler(ar, responsePromise, HttpURLConnection.HTTP_OK, "Error doing prometheus query " + uri));
        return responsePromise.get(150000, TimeUnit.SECONDS);
    }

    public JsonObject doRangeQuery(String query, String startTs, String endTs) throws Exception {
        Objects.requireNonNull(query);
        Objects.requireNonNull(startTs);
        Objects.requireNonNull(endTs);
        CompletableFuture<JsonObject> responsePromise = new CompletableFuture<>();
        String uri = String.format("/api/v1/query_range?query=%s&start=%s&end=%s&step=14", query, startTs, endTs);
        getClient().get(port, host, uri)
                .bearerTokenAuthentication(token)
                .as(BodyCodec.jsonObject())
                .timeout(120000)
                .send(ar -> responseHandler(ar, responsePromise, HttpURLConnection.HTTP_OK, "Error doing prometheus range query " + uri));
        return responsePromise.get(150000, TimeUnit.SECONDS);
    }

}
