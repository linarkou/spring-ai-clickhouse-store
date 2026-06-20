package org.springaicommunity.ai.vectorstore.clickhouse.autoconfigure;

import com.clickhouse.client.api.Client;
import io.micrometer.observation.ObservationRegistry;
import org.springaicommunity.ai.vectorstore.clickhouse.ClickHouseVectorStore;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for ClickHouse Vector Store.
 *
 * @author Linar Abzaltdinov
 */
@AutoConfiguration(after = ClickHouseClientAutoConfiguration.class)
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
                .customObservationConvention(customObservationConvention.getIfAvailable())
                .batchingStrategy(batchingStrategy);

        return builder.build();
    }
}
