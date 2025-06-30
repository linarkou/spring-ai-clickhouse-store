/*
 * Copyright 2023-2024 the original author or authors.
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

import com.clickhouse.client.api.Client;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.clickhouse.ClickHouseVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.CollectionUtils;

/**
 * {@link AutoConfiguration Auto-configuration} for ClickHouse Vector Store.
 *
 * @author Linar Abzaltdinov
 */
@AutoConfiguration
@ConditionalOnClass({EmbeddingModel.class, Client.class, ClickHouseVectorStore.class})
@EnableConfigurationProperties({ClickHouseVectorStoreProperties.class, ClickHouseClientProperties.class})
@ConditionalOnProperty(
        name = SpringAIVectorStoreTypes.TYPE,
        havingValue = ClickHouseVectorStore.CLICKHOUSE_VECTOR_STORE,
        matchIfMissing = true)
public class ClickHouseVectorStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(BatchingStrategy.class)
    BatchingStrategy batchingStrategy() {
        return new TokenCountBatchingStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public ClickHouseVectorStore vectorStore(
            Client clickHouseClient,
            EmbeddingModel embeddingModel,
            ClickHouseVectorStoreProperties properties,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
            BatchingStrategy batchingStrategy) {
        var builder = ClickHouseVectorStore.builder(clickHouseClient, embeddingModel)
                .initializeSchema(properties.isInitializeSchema())
                .databaseName(properties.getDatabaseName())
                .tableName(properties.getTableName())
                .idColumnName(properties.getIdColumnName())
                .embeddingColumnName(properties.getEmbeddingColumnName())
                .contentColumnName(properties.getContentColumnName())
                .metadataColumnName(properties.getMetadataColumnName())
                .distanceType(properties.getDistanceType())
                .timeout(properties.getRequestTimeout().toMillis())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
                .batchingStrategy(batchingStrategy);

        return builder.build();
    }

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
                properties.getKeepAliveTimeout(),
                timeout -> clientBuilder.setKeepAliveTimeout(timeout.toMillis(), ChronoUnit.MILLIS));
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

    private <T> void setIfNotNull(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
