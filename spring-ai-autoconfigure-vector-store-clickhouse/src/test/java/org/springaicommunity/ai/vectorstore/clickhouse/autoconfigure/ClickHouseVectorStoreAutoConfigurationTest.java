package org.springaicommunity.ai.vectorstore.clickhouse.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.clickhouse.client.api.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springaicommunity.ai.vectorstore.clickhouse.ClickHouseVectorStore;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClickHouseVectorStoreAutoConfigurationTest {

    private final Client client = mock(Client.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ClickHouseClientAutoConfiguration.class, ClickHouseVectorStoreAutoConfiguration.class))
            .withBean(Client.class, () -> client)
            .withBean(EmbeddingModel.class, () -> embeddingModel);

    @Test
    void createsStoreWithoutInitializingSchemaByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ClickHouseVectorStore.class);
            assertThat(context.getBean(ClickHouseVectorStore.class).<Client>getNativeClient())
                    .containsSame(client);
            assertThat(context).hasSingleBean(BatchingStrategy.class);
            assertThat(context.getBean(ClickHouseVectorStoreProperties.class).isInitializeSchema())
                    .isFalse();
            verifyNoInteractions(client, embeddingModel);
        });
    }

    @Test
    void enablesExplicitClickHouseType() {
        contextRunner
                .withPropertyValues("spring.ai.vectorstore.type=clickhouse")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ClickHouseVectorStore.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "pgvector"})
    void disablesStoreForOtherTypes(String type) {
        contextRunner.withPropertyValues("spring.ai.vectorstore.type=" + type).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(ClickHouseVectorStore.class);
            assertThat(context).doesNotHaveBean(ClickHouseVectorStoreProperties.class);
        });
    }

    @Test
    void backsOffForUserProvidedStoreAndBatchingStrategy() {
        var store = ClickHouseVectorStore.builder(client, embeddingModel).build();
        BatchingStrategy batchingStrategy = new TokenCountBatchingStrategy();
        contextRunner
                .withBean(ClickHouseVectorStore.class, () -> store)
                .withBean(BatchingStrategy.class, () -> batchingStrategy)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ClickHouseVectorStore.class);
                    assertThat(context.getBean(ClickHouseVectorStore.class)).isSameAs(store);
                    assertThat(context.getBean(BatchingStrategy.class)).isSameAs(batchingStrategy);
                });
    }

    @Test
    void backsOffWhenNativeClientIsNotOnClasspath() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ClickHouseClientAutoConfiguration.class, ClickHouseVectorStoreAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(Client.class))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ClickHouseVectorStore.class));
    }
}
