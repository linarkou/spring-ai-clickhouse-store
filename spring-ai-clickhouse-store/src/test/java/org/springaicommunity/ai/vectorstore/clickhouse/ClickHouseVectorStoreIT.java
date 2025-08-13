package org.springaicommunity.ai.vectorstore.clickhouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.data_formats.internal.BinaryStreamReader;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.client.api.query.Records;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.BaseVectorStoreTests;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Linar Abzaltdinov
 */
@Testcontainers
class ClickHouseVectorStoreIT extends BaseVectorStoreTests {

    @Container
    static ClickHouseContainer clickHouseContainer = new ClickHouseContainer(
                    DockerImageName.parse("clickhouse/clickhouse-server:latest"))
            .withDatabaseName("default")
            .withUsername("username")
            .withPassword("password");

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(EmbeddingModelAndClickhouseClientConfiguration.class);

    private static List<Document> documents() {
        return List.of(
                new Document(
                        "1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1", "intMeta", 456)),
                new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
                new Document(
                        "3",
                        getText("classpath:/test/data/great.depression.txt"),
                        Map.of("meta2", "meta2", "intMeta", 123)));
    }

    private static String getText(String uri) {
        var resource = new DefaultResourceLoader().getResource(uri);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeAll
    static void beforeAll() {
        clickHouseContainer.start();
    }

    @AfterAll
    static void afterAll() {
        clickHouseContainer.stop();
    }

    @Test
    void testInitializationWithCustomNames() {
        this.contextRunner.run(context -> {
            ClickHouseVectorStore clickhouseVectorStore = createClickHouseVectorStoreBuilder(context)
                    .databaseName("test_db_name")
                    .tableName("test_table")
                    .idColumnName("test_id")
                    .embeddingColumnName("test_embedding")
                    .contentColumnName("test_content")
                    .metadataColumnName("test_metadata")
                    .initializeSchema(true)
                    .build();
            clickhouseVectorStore.afterPropertiesSet();

            EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
            Client chClient = context.getBean(Client.class);

            int dimensions = embeddingModel.dimensions();
            double[] testEmbeddingArray =
                    new Random().doubles(dimensions, 0.0, 1.0).toArray();
            String testEmbedding = toString(testEmbeddingArray);

            chClient.execute(
                    "insert into test_db_name.test_table " + "(test_id, test_embedding, test_content, test_metadata) "
                            + "values ('1', "
                            + testEmbedding + ", 'test content', '{\"a\": \"b\"}')");

            Records records = chClient.queryRecords("select * from test_db_name.test_table")
                    .get();
            Iterator<GenericRecord> iterator = records.iterator();
            GenericRecord record = iterator.next();
            assertFalse(iterator.hasNext());
            assertEquals("1", record.getString("test_id"));
            assertEquals("test content", record.getString("test_content"));
            assertEquals(Map.of("a", "b"), record.getObject("test_metadata"));
            var actualEmbedding =
                    (BinaryStreamReader.ArrayValue) record.getValues().get("test_embedding");
            double[] actualEmbeddingArray = actualEmbedding.asList().stream()
                    .mapToDouble(v -> (Double) v)
                    .toArray();
            //            var actualEmbedding = record.getDoubleArray("test_embedding");  // doesn't work
            assertEquals(toString(testEmbeddingArray), toString(actualEmbeddingArray));

            chClient.execute("drop table test_db_name.test_table").get();
            clickhouseVectorStore.close();
        });
    }

    public static Stream<Arguments> filterExpressions() {
        var b = new FilterExpressionBuilder();
        return Stream.of(
                Arguments.of(b.eq("meta1", "meta1").build(), "meta1 == 'meta1'", List.of("1")),
                Arguments.of(
                        b.ne("meta1", null).build(),
                        // "meta1 != null", - https://github.com/spring-projects/spring-ai/issues/3694
                        null, // native filter expression 'is not null' is not supported
                        List.of("1")),
                Arguments.of(
                        b.eq("meta1", null).build(),
                        // "meta1 == null", - https://github.com/spring-projects/spring-ai/issues/3694
                        null, // native filter expression 'is null' is not supported
                        List.of("2", "3")),
                Arguments.of(
                        b.or(b.eq("meta1", "meta1"), b.eq("meta2", "meta2")).build(),
                        "meta1 == 'meta1' OR meta2 == 'meta2'",
                        List.of("1", "3")),
                Arguments.of(
                        b.and(b.eq("meta1", "meta1"), b.eq("meta2", "meta2")).build(),
                        "meta1 == 'meta1' AND meta2 == 'meta2'",
                        List.of()),
                Arguments.of(b.eq("intMeta", 123).build(), "intMeta == 123'", List.of("3")),
                Arguments.of(b.gte("intMeta", 123).build(), "intMeta >= 123'", List.of("1", "3")),
                Arguments.of(b.lte("intMeta", 123).build(), "intMeta <= 123'", List.of("3")),
                Arguments.of(b.gt("intMeta", 120).build(), "intMeta > 120'", List.of("1", "3")),
                Arguments.of(b.lt("intMeta", 130).build(), "intMeta < 130'", List.of("3")),
                Arguments.of(
                        b.in("intMeta::UInt32", 123, 456).build(),
                        null,
                        //                      "intMeta in (123, 456)",// doesn't work -
                        // https://github.com/ClickHouse/ClickHouse/issues/81012
                        List.of("1", "3")),
                Arguments.of(
                        b.in("meta1::String", "meta1", "meta2").build(),
                        null,
                        //                        "meta1 in ('meta1', 'meta2')",// doesn't work -
                        // https://github.com/ClickHouse/ClickHouse/issues/81012
                        List.of("1")),
                Arguments.of(
                        b.nin("intMeta::UInt32", 123).build(),
                        null,
                        //                        "intMeta not in (123)",// doesn't work -
                        // https://github.com/ClickHouse/ClickHouse/issues/81012
                        List.of("1", "2")));
    }

    @MethodSource("filterExpressions")
    @ParameterizedTest
    void testSimilaritySearchWithFilters(
            Filter.Expression filterExpression, String nativeFilterExpression, List<String> expectedIds) {
        this.contextRunner.withUserConfiguration(VectorStoreConfiguration.class).run(context -> {
            ClickHouseVectorStore clickhouseVectorStore = context.getBean(ClickHouseVectorStore.class);

            List<Document> originalDocuments = documents();
            clickhouseVectorStore.doAdd(originalDocuments);

            if (filterExpression != null) {
                SearchRequest searchRequest = SearchRequest.builder()
                        .query("Spring AI")
                        .similarityThresholdAll()
                        .topK(3)
                        .filterExpression(filterExpression)
                        .build();
                List<Document> documents = clickhouseVectorStore.doSimilaritySearch(searchRequest);
                assertEquals(
                        expectedIds, documents.stream().map(Document::getId).collect(Collectors.toList()));
            }

            if (nativeFilterExpression != null) {
                SearchRequest searchRequest = SearchRequest.builder()
                        .query("Spring AI")
                        .similarityThresholdAll()
                        .topK(3)
                        .filterExpression(nativeFilterExpression)
                        .build();
                List<Document> documents = clickhouseVectorStore.doSimilaritySearch(searchRequest);
                assertEquals(
                        expectedIds, documents.stream().map(Document::getId).collect(Collectors.toList()));
            }

            clickhouseVectorStore.doDelete(
                    originalDocuments.stream().map(Document::getId).toList());
            clickhouseVectorStore.close();
        });
    }

    private static ClickHouseVectorStore createClickHouseVectorStore(ApplicationContext context) {
        return createClickHouseVectorStoreBuilder(context).build();
    }

    private static ClickHouseVectorStore.Builder createClickHouseVectorStoreBuilder(ApplicationContext context) {
        return ClickHouseVectorStore.builder(context.getBean(Client.class), context.getBean(EmbeddingModel.class));
    }

    private static String toString(double[] array) {
        return "[" + Arrays.stream(array).mapToObj(String::valueOf).collect(Collectors.joining(",")) + "]";
    }

    @Override
    protected void executeTest(Consumer<VectorStore> testFunction) {
        this.contextRunner.withUserConfiguration(VectorStoreConfiguration.class).run(context -> {
            VectorStore vectorStore = context.getBean(VectorStore.class);
            testFunction.accept(vectorStore);
        });
    }

    @SpringBootConfiguration
    public static class EmbeddingModelAndClickhouseClientConfiguration {

        @Bean
        public EmbeddingModel embeddingModel() {
            return new TransformersEmbeddingModel();
        }

        @Bean
        public Client clickhouseClient() {
            return new Client.Builder()
                    .addEndpoint(
                            "http://" + clickHouseContainer.getHost() + ":" + clickHouseContainer.getMappedPort(8123))
                    .setUsername(clickHouseContainer.getUsername())
                    .setPassword(clickHouseContainer.getPassword())
                    .serverSetting("allow_experimental_vector_similarity_index", "1")
                    .serverSetting("enable_json_type", "1")
                    .build();
        }
    }

    @SpringBootConfiguration
    public static class VectorStoreConfiguration {

        @Bean
        public VectorStore vectorStore(Client clickHouseClient, EmbeddingModel embeddingModel) {
            return ClickHouseVectorStore.builder(clickHouseClient, embeddingModel)
                    .distanceType(ClickHouseVectorStore.DistanceType.COSINE)
                    .initializeSchema(true)
                    .build();
        }
    }
}
