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

package org.springaicommunity.ai.vectorstore.clickhouse.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springaicommunity.ai.vectorstore.clickhouse.ClickHouseVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Linar Abzaltdinov
 */
@Testcontainers
public class ClickHouseVectorStoreAutoConfigurationIT {

    @Container
    static ClickHouseContainer clickHouseContainer = new ClickHouseContainer(
                    DockerImageName.parse("clickhouse/clickhouse-server:latest"))
            .withDatabaseName("default")
            .withUsername("username")
            .withPassword("password");

    private static String getConnectionString() {
        return "http://" + clickHouseContainer.getHost() + ":" + clickHouseContainer.getMappedPort(8123);
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ClickHouseVectorStoreAutoConfiguration.class))
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "spring.ai.vectorstore.clickhouse.client.endpoints[0]=" + getConnectionString(),
                    "spring.ai.vectorstore.clickhouse.client.username=username",
                    "spring.ai.vectorstore.clickhouse.client.password=password")
            .withPropertyValues("spring.ai.vectorstore.clickhouse.initialize-schema=true");

    List<Document> documents = List.of(
            new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
            new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
            new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

    public static String getText(String uri) {
        var resource = new DefaultResourceLoader().getResource(uri);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeAll
    public static void beforeAll() {
        Awaitility.setDefaultPollInterval(2, TimeUnit.SECONDS);
        Awaitility.setDefaultPollDelay(Duration.ZERO);
        Awaitility.setDefaultTimeout(Duration.ofMinutes(1));
    }

    @Test
    public void addAndSearchTest() {
        this.contextRunner.run(context -> {
            var vectorStoreProperties = context.getBean(ClickHouseVectorStoreProperties.class);

            assertThat(vectorStoreProperties.getDatabaseName()).isEqualTo(ClickHouseVectorStore.DEFAULT_DATABASE_NAME);
            assertThat(vectorStoreProperties.getTableName()).isEqualTo(ClickHouseVectorStore.DEFAULT_TABLE_NAME);
            assertThat(vectorStoreProperties.getIdColumnName()).isEqualTo(ClickHouseVectorStore.DEFAULT_ID_COLUMN_NAME);
            assertThat(vectorStoreProperties.getContentColumnName())
                    .isEqualTo(ClickHouseVectorStore.DEFAULT_CONTENT_COLUMN_NAME);
            assertThat(vectorStoreProperties.getEmbeddingColumnName())
                    .isEqualTo(ClickHouseVectorStore.DEFAULT_EMBEDDING_COLUMN_NAME);
            assertThat(vectorStoreProperties.getMetadataColumnName())
                    .isEqualTo(ClickHouseVectorStore.DEFAULT_METADATA_COLUMN_NAME);

            VectorStore vectorStore = context.getBean(VectorStore.class);
            TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

            assertThat(vectorStore).isInstanceOf(ClickHouseVectorStore.class);

            vectorStore.add(this.documents);

            Awaitility.await()
                    .until(
                            () -> vectorStore.similaritySearch(SearchRequest.builder()
                                    .query("Spring")
                                    .topK(1)
                                    .build()),
                            hasSize(1));

            assertObservationRegistry(
                    observationRegistry,
                    ClickHouseVectorStore.CLICKHOUSE_VECTOR_STORE,
                    VectorStoreObservationContext.Operation.ADD);
            observationRegistry.clear();

            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder().query("Spring").topK(1).build());

            assertThat(results).hasSize(1);
            Document resultDoc = results.get(0);
            assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
            assertThat(resultDoc.getText())
                    .contains(
                            "Spring AI provides abstractions that serve as the foundation for developing AI applications.");
            assertThat(resultDoc.getMetadata()).hasSize(2);
            assertThat(resultDoc.getMetadata()).containsKeys("spring", "distance");

            assertObservationRegistry(
                    observationRegistry,
                    ClickHouseVectorStore.CLICKHOUSE_VECTOR_STORE,
                    VectorStoreObservationContext.Operation.QUERY);
            observationRegistry.clear();

            // Remove all documents from the store
            vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

            Awaitility.await()
                    .until(
                            () -> vectorStore.similaritySearch(SearchRequest.builder()
                                    .query("Spring")
                                    .topK(1)
                                    .build()),
                            hasSize(0));

            assertObservationRegistry(
                    observationRegistry,
                    ClickHouseVectorStore.CLICKHOUSE_VECTOR_STORE,
                    VectorStoreObservationContext.Operation.DELETE);
            observationRegistry.clear();
        });
    }

    private void assertObservationRegistry(
            TestObservationRegistry observationRegistry,
            String vectorStoreProviderName,
            VectorStoreObservationContext.Operation operation) {
        TestObservationRegistryAssert.assertThat(observationRegistry)
                .doesNotHaveAnyRemainingCurrentObservation()
                .hasObservationWithNameEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME)
                .that()
                .hasContextualNameEqualTo(vectorStoreProviderName + " " + operation.value())
                .hasBeenStarted()
                .hasBeenStopped();
    }

    @Test
    public void autoConfigurationDisabledWhenTypeIsNone() {
        this.contextRunner.withPropertyValues("spring.ai.vectorstore.type=none").run(context -> {
            assertThat(context.getBeansOfType(ClickHouseVectorStoreProperties.class))
                    .isEmpty();
            assertThat(context.getBeansOfType(ClickHouseVectorStore.class)).isEmpty();
            assertThat(context.getBeansOfType(VectorStore.class)).isEmpty();
        });
    }

    @Test
    public void autoConfigurationEnabledByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context.getBeansOfType(ClickHouseVectorStoreProperties.class))
                    .isNotEmpty();
            assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
            assertThat(context.getBean(VectorStore.class)).isInstanceOf(ClickHouseVectorStore.class);
        });
    }

    @Test
    public void autoConfigurationEnabledWhenTypeIsClickHouse() {
        this.contextRunner
                .withPropertyValues("spring.ai.vectorstore.type=clickhouse")
                .run(context -> {
                    assertThat(context.getBeansOfType(ClickHouseVectorStoreProperties.class))
                            .isNotEmpty();
                    assertThat(context.getBeansOfType(VectorStore.class)).isNotEmpty();
                    assertThat(context.getBean(VectorStore.class)).isInstanceOf(ClickHouseVectorStore.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {

        @Bean
        public TestObservationRegistry observationRegistry() {
            return TestObservationRegistry.create();
        }

        @Bean
        public EmbeddingModel embeddingModel() {
            return new TransformersEmbeddingModel();
        }
    }
}
