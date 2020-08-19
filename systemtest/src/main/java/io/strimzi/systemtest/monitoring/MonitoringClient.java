/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest.monitoring;

import io.strimzi.systemtest.Constants;
import io.strimzi.systemtest.apiclients.PrometheusApiClient;
import io.strimzi.test.TestUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;


public class MonitoringClient {
    private static final Logger log = LogManager.getLogger(MonitoringClient.class);
    private final PrometheusApiClient client;

    public MonitoringClient(String host, int port) {
        this.client = new PrometheusApiClient(host, port);
    }

    public void validateQueryAndWait(String query, String expectedValue) {
        validateQueryAndWait(query, expectedValue, Collections.emptyMap());
    }

    public void validateQueryAndWait(String query, String expectedValue, Map<String, String> labels) {
        TestUtils.waitFor(String.format("Query: %s, expected value: %s", query, expectedValue), Constants.GLOBAL_POLL_INTERVAL, Constants.GLOBAL_TIMEOUT, () -> {
            try {
                validateQuery(query, expectedValue, labels);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    public void validateRangeQueryAndWait(String query, Instant start, Predicate<List<String>> rangeValidator) {
        TestUtils.waitFor(String.format("Range query: %s, from %s to now", query, start), Constants.GLOBAL_POLL_INTERVAL, Constants.GLOBAL_TIMEOUT, () -> {
            try {
                validateRangeQuery(query, start, query, rangeValidator);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    public void validateQuery(String query, String expectedValue, Map<String, String> labels) throws Exception {
        JsonObject queryResult = client.doQuery(query);
        basicQueryResultValidation(query, queryResult);
        boolean validateResult = metricQueryResultValidation(queryResult, query, labels, resource ->
                expectedValue.equals(resource.getValue()));
        if (!validateResult) {
            throw new Exception("Unexpected query result " + queryResult.encodePrettily());
        }
    }

    public void validateRangeQuery(String query, Instant start, String addressSpace, Predicate<List<String>> rangeValidator) throws Exception {
        JsonObject queryResult = client.doRangeQuery(query,
                String.valueOf(start.getEpochSecond()), String.valueOf(Instant.now().getEpochSecond()));
        basicQueryResultValidation(query, queryResult);
        boolean validateResult = metricQueryResultValidation(queryResult, addressSpace, Collections.emptyMap(), resource ->
                rangeValidator.test(resource.getRangeValues()));
        if (!validateResult) {
            throw new Exception("Unexpected query result " + queryResult.encodePrettily());
        }
    }

    public void waitUntilPrometheusReady() {
        TestUtils.waitFor("Prometheus ready", Constants.GLOBAL_POLL_INTERVAL, Constants.GLOBAL_TIMEOUT, () -> {
            try {
                JsonObject rule = getRule("strimzi_reconciliations_successful_total");
                if (rule != null) {
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
            return false;
        });
    }

    public PrometheusMetricResource getQueryResult(String query, Map<String, String> labels) throws Exception {
        JsonObject response = client.doQuery(query);
        basicQueryResultValidation(query, response);
        return PrometheusMetricResource.getResource(response, query, labels);
    }

    //=============================================================================
    // Help methods
    //=============================================================================
    private void basicQueryResultValidation(String query, JsonObject queryResult) throws Exception {
        if (queryResult == null) {
            throw new Exception("Result of query " + query + " is null");
        }
        if (!queryResult.getString("status", "").equals("success")) {
            throw new Exception("Failed doing query " + queryResult.encodePrettily());
        }
    }

    private boolean metricQueryResultValidation(JsonObject queryResult, String metricName,
                                                Map<String, String> labels, Predicate<PrometheusMetricResource> resultValidator) {
        PrometheusMetricResource data = PrometheusMetricResource.getResource(queryResult, metricName, labels);
        if (data != null) {
            return resultValidator.test(data);
        }
        return false;
    }

    private JsonObject getRule(String name) throws Exception {
        JsonObject rules = client.getRules();
        if (rules.getString("status", "").equals("success")) {
            JsonObject data = rules.getJsonObject("data", new JsonObject());
            for (Object obj : data.getJsonArray("groups", new JsonArray())) {
                JsonObject group = (JsonObject) obj;
                for (Object ruleObj : group.getJsonArray("rules", new JsonArray())) {
                    JsonObject rule = (JsonObject) ruleObj;
                    if (rule.getString("name").equals(name)) {
                        return rule;
                    }
                }
            }
        }
        return null;
    }
}
