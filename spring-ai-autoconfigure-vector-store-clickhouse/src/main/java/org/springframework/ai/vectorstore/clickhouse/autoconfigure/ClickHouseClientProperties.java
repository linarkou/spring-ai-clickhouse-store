/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.vectorstore.clickhouse.autoconfigure;

import java.time.Duration;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for ClickHouse Java Client.
 *
 * @author Linar Abzaltdinov
 */
@ConfigurationProperties(ClickHouseClientProperties.CONFIG_PREFIX)
public class ClickHouseClientProperties {

    public static final String CONFIG_PREFIX = "spring.ai.vectorstore.clickhouse.client";

    private Set<String> endpoints = new HashSet<>();

    private String username;
    private String password;
    private String accessToken;

    private String defaultDatabaseName;

    private Boolean sslAuthentication;

    private String sslTrustStorePath;
    private String sslTrustStorePassword;
    private String sslTrustStoreType;

    private String rootCertificatePath;
    private String clientCertificatePath;
    private String clientKeyPath;

    private Duration connectTimeout;
    private Duration connectionRequestTimeout;
    private Duration connectionTtl;
    private Duration socketTimeout;
    private Duration executionTimeout;

    private boolean useMeterRegistry = true;
    private String metricsGroupName = "clickhouse-client-metrics";

    private Map<String, String> httpHeaders = new HashMap<>();

    private Map<String, String> serverSettings =
            new HashMap<>(Map.of("allow_experimental_vector_similarity_index", "1"));
    private Map<String, String> options = new HashMap<>();

    public Set<String> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(Set<String> endpoints) {
        Objects.requireNonNull(endpoints, "endpoints cannot be null");
        this.endpoints = endpoints;
    }

    public void addEndpoint(String endpoint) {
        this.endpoints.add(endpoint);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getDefaultDatabaseName() {
        return defaultDatabaseName;
    }

    public void setDefaultDatabaseName(String defaultDatabaseName) {
        this.defaultDatabaseName = defaultDatabaseName;
    }

    public Boolean getSslAuthentication() {
        return sslAuthentication;
    }

    public void setSslAuthentication(Boolean sslAuthentication) {
        this.sslAuthentication = sslAuthentication;
    }

    public String getSslTrustStorePath() {
        return sslTrustStorePath;
    }

    public void setSslTrustStorePath(String sslTrustStorePath) {
        this.sslTrustStorePath = sslTrustStorePath;
    }

    public String getSslTrustStorePassword() {
        return sslTrustStorePassword;
    }

    public void setSslTrustStorePassword(String sslTrustStorePassword) {
        this.sslTrustStorePassword = sslTrustStorePassword;
    }

    public String getSslTrustStoreType() {
        return sslTrustStoreType;
    }

    public void setSslTrustStoreType(String sslTrustStoreType) {
        this.sslTrustStoreType = sslTrustStoreType;
    }

    public String getRootCertificatePath() {
        return rootCertificatePath;
    }

    public void setRootCertificatePath(String rootCertificatePath) {
        this.rootCertificatePath = rootCertificatePath;
    }

    public String getClientCertificatePath() {
        return clientCertificatePath;
    }

    public void setClientCertificatePath(String clientCertificatePath) {
        this.clientCertificatePath = clientCertificatePath;
    }

    public String getClientKeyPath() {
        return clientKeyPath;
    }

    public void setClientKeyPath(String clientKeyPath) {
        this.clientKeyPath = clientKeyPath;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    public void setConnectionRequestTimeout(Duration connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    public Duration getConnectionTtl() {
        return connectionTtl;
    }

    public void setConnectionTtl(Duration connectionTtl) {
        this.connectionTtl = connectionTtl;
    }

    public Duration getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(Duration socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    public void setExecutionTimeout(Duration executionTimeout) {
        this.executionTimeout = executionTimeout;
    }

    public boolean isUseMeterRegistry() {
        return useMeterRegistry;
    }

    public void setUseMeterRegistry(boolean useMeterRegistry) {
        this.useMeterRegistry = useMeterRegistry;
    }

    public String getMetricsGroupName() {
        return metricsGroupName;
    }

    public void setMetricsGroupName(String metricsGroupName) {
        Objects.requireNonNull(metricsGroupName, "metricsGroupName cannot be null");
        this.metricsGroupName = metricsGroupName;
    }

    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }

    public void setHttpHeaders(Map<String, String> httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    public void addHttpHeader(String key, String value) {
        this.httpHeaders.put(key, value);
    }

    public Map<String, String> getServerSettings() {
        return serverSettings;
    }

    public void setServerSettings(Map<String, String> serverSettings) {
        this.serverSettings = serverSettings;
    }

    public void addServerSetting(String key, String value) {
        this.serverSettings.put(key, value);
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }

    public void addOption(String key, String value) {
        this.options.put(key, value);
    }
}
