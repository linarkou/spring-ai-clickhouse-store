package org.springaicommunity.ai.vectorstore.clickhouse.autoconfigure;

import java.time.Duration;
import java.util.*;
import org.jspecify.annotations.Nullable;
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

    @Nullable
    private String username;

    @Nullable
    private String password;

    @Nullable
    private String accessToken;

    @Nullable
    private String defaultDatabaseName;

    @Nullable
    private Boolean sslAuthentication;

    @Nullable
    private String sslTrustStorePath;

    @Nullable
    private String sslTrustStorePassword;

    @Nullable
    private String sslTrustStoreType;

    @Nullable
    private String rootCertificatePath;

    @Nullable
    private String clientCertificatePath;

    @Nullable
    private String clientKeyPath;

    @Nullable
    private Duration connectTimeout;

    @Nullable
    private Duration connectionRequestTimeout;

    @Nullable
    private Duration connectionTtl;

    @Nullable
    private Duration socketTimeout;

    @Nullable
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

    @Nullable
    public String getUsername() {
        return username;
    }

    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    @Nullable
    public String getPassword() {
        return password;
    }

    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    @Nullable
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(@Nullable String accessToken) {
        this.accessToken = accessToken;
    }

    @Nullable
    public String getDefaultDatabaseName() {
        return defaultDatabaseName;
    }

    public void setDefaultDatabaseName(@Nullable String defaultDatabaseName) {
        this.defaultDatabaseName = defaultDatabaseName;
    }

    @Nullable
    public Boolean getSslAuthentication() {
        return sslAuthentication;
    }

    public void setSslAuthentication(@Nullable Boolean sslAuthentication) {
        this.sslAuthentication = sslAuthentication;
    }

    @Nullable
    public String getSslTrustStorePath() {
        return sslTrustStorePath;
    }

    public void setSslTrustStorePath(@Nullable String sslTrustStorePath) {
        this.sslTrustStorePath = sslTrustStorePath;
    }

    @Nullable
    public String getSslTrustStorePassword() {
        return sslTrustStorePassword;
    }

    public void setSslTrustStorePassword(@Nullable String sslTrustStorePassword) {
        this.sslTrustStorePassword = sslTrustStorePassword;
    }

    @Nullable
    public String getSslTrustStoreType() {
        return sslTrustStoreType;
    }

    public void setSslTrustStoreType(@Nullable String sslTrustStoreType) {
        this.sslTrustStoreType = sslTrustStoreType;
    }

    @Nullable
    public String getRootCertificatePath() {
        return rootCertificatePath;
    }

    public void setRootCertificatePath(@Nullable String rootCertificatePath) {
        this.rootCertificatePath = rootCertificatePath;
    }

    @Nullable
    public String getClientCertificatePath() {
        return clientCertificatePath;
    }

    public void setClientCertificatePath(@Nullable String clientCertificatePath) {
        this.clientCertificatePath = clientCertificatePath;
    }

    @Nullable
    public String getClientKeyPath() {
        return clientKeyPath;
    }

    public void setClientKeyPath(@Nullable String clientKeyPath) {
        this.clientKeyPath = clientKeyPath;
    }

    @Nullable
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(@Nullable Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    @Nullable
    public Duration getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    public void setConnectionRequestTimeout(@Nullable Duration connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    @Nullable
    public Duration getConnectionTtl() {
        return connectionTtl;
    }

    public void setConnectionTtl(@Nullable Duration connectionTtl) {
        this.connectionTtl = connectionTtl;
    }

    @Nullable
    public Duration getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(@Nullable Duration socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    @Nullable
    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    public void setExecutionTimeout(@Nullable Duration executionTimeout) {
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
