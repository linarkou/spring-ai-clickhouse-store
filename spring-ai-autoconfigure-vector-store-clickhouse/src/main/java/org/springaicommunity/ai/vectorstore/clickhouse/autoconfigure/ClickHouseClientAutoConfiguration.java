package org.springaicommunity.ai.vectorstore.clickhouse.autoconfigure;

import com.clickhouse.client.api.Client;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.CollectionUtils;

/**
 * {@link AutoConfiguration Auto-configuration} for ClickHouse Client.
 *
 * @author Linar Abzaltdinov
 */
@AutoConfiguration
@ConditionalOnClass(Client.class)
@EnableConfigurationProperties(ClickHouseClientProperties.class)
@ConditionalOnProperty(
        name = ClickHouseClientProperties.CONFIG_PREFIX + ".enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ClickHouseClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Client clickHouseClient(
            ClickHouseClientProperties properties, ObjectProvider<MeterRegistry> meterRegistryProvider) {
        var clientBuilder = new Client.Builder();
        properties.getEndpoints().forEach(clientBuilder::addEndpoint);
        setIfNotNull(properties.getUsername(), clientBuilder::setUsername);
        setIfNotNull(properties.getPassword(), clientBuilder::setPassword);
        setIfNotNull(properties.getAccessToken(), clientBuilder::setAccessToken);
        setIfNotNull(properties.getDefaultDatabaseName(), clientBuilder::setDefaultDatabase);
        setIfNotNull(properties.getSslAuthentication(), clientBuilder::useSSLAuthentication);
        setIfNotNull(properties.getSslTrustStorePath(), clientBuilder::setSSLTrustStore);
        setIfNotNull(properties.getSslTrustStorePassword(), clientBuilder::setSSLTrustStorePassword);
        setIfNotNull(properties.getSslTrustStoreType(), clientBuilder::setSSLTrustStoreType);
        setIfNotNull(properties.getRootCertificatePath(), clientBuilder::setRootCertificate);
        setIfNotNull(properties.getClientCertificatePath(), clientBuilder::setClientCertificate);
        setIfNotNull(properties.getClientKeyPath(), clientBuilder::setClientKey);
        setIfNotNull(properties.getConnectTimeout(), timeout -> clientBuilder.setConnectTimeout(timeout.toMillis()));
        setIfNotNull(
                properties.getConnectionRequestTimeout(),
                timeout -> clientBuilder.setConnectionRequestTimeout(timeout.toMillis(), ChronoUnit.MILLIS));
        setIfNotNull(
                properties.getConnectionTtl(),
                timeout -> clientBuilder.setConnectionTTL(timeout.toMillis(), ChronoUnit.MILLIS));
        setIfNotNull(
                properties.getSocketTimeout(),
                timeout -> clientBuilder.setSocketTimeout(timeout.toMillis(), ChronoUnit.MILLIS));
        setIfNotNull(
                properties.getExecutionTimeout(),
                timeout -> clientBuilder.setExecutionTimeout(timeout.toMillis(), ChronoUnit.MILLIS));
        setIfNotNull(
                properties.getExecutionTimeout(),
                timeout -> clientBuilder.setExecutionTimeout(timeout.toMillis(), ChronoUnit.MILLIS));
        if (properties.isUseMeterRegistry()) {
            MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new);
            clientBuilder.registerClientMetrics(meterRegistry, properties.getMetricsGroupName());
        }
        if (!CollectionUtils.isEmpty(properties.getHttpHeaders())) {
            clientBuilder.httpHeaders(properties.getHttpHeaders());
        }
        if (!CollectionUtils.isEmpty(properties.getServerSettings())) {
            properties.getServerSettings().forEach(clientBuilder::serverSetting);
        }
        if (!CollectionUtils.isEmpty(properties.getOptions())) {
            clientBuilder.setOptions(properties.getOptions());
        }
        return clientBuilder.build();
    }

    private <T> void setIfNotNull(@Nullable T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
