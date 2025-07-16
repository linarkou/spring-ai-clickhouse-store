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

package org.springframework.ai.vectorstore.clickhouse;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.metrics.ServerMetrics;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.data.ClickHouseFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * The ClickhouseVectorStore is for managing and querying vector data in a Clickhouse db.
 * It offers functionalities like adding, deleting, and performing
 * similarity searches on documents.
 *
 * @author Linar Abzaltdinov
 * @see VectorStore
 * @see EmbeddingModel
 * @since 1.0.0
 */
public class ClickHouseVectorStore extends AbstractObservationVectorStore implements InitializingBean, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseVectorStore.class);
    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper().setSerializationInclusion(NON_NULL);
    private static final Map<DistanceType, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(
            DistanceType.COSINE, VectorStoreSimilarityMetric.COSINE,
            DistanceType.L2, VectorStoreSimilarityMetric.EUCLIDEAN);

    public static final String CLICKHOUSE_VECTOR_STORE = "clickhouse";
    public static final String DEFAULT_DATABASE_NAME = "ai";
    public static final String DEFAULT_TABLE_NAME = "vector_store";
    public static final String DEFAULT_ID_COLUMN_NAME = "id";
    public static final String DEFAULT_EMBEDDING_COLUMN_NAME = "embedding";
    public static final String DEFAULT_CONTENT_COLUMN_NAME = "content";
    public static final String DEFAULT_METADATA_COLUMN_NAME = "metadata";
    public static final DistanceType DEFAULT_DISTANCE_TYPE = DistanceType.COSINE;
    public static final boolean DEFAULT_INITIALIZE_SCHEMA = false;
    public static final long DEFAULT_TIMEOUT = 10_000L; // milliseconds
    public static final String DISTANCE_COLUMN_NAME = "distance";

    private final Client client;
    private final ObjectMapper objectMapper;

    private String databaseName;
    private String tableName;
    private String idColumnName;
    private String embeddingColumnName;
    private String contentColumnName;
    private String metadataColumnName;
    private DistanceType distanceType;
    private boolean initializeSchema;
    private long timeout;
    private ClickHouseFilterExpressionConverter filterExpressionConverter;

    protected ClickHouseVectorStore(Builder builder) {
        super(builder);

        Assert.notNull(builder.client, "ClickHouse client must not be null");

        this.client = builder.client;
        this.objectMapper = builder.objectMapper;
        this.databaseName = builder.databaseName;
        this.tableName = builder.tableName;
        this.idColumnName = builder.idColumnName;
        this.embeddingColumnName = builder.embeddingColumnName;
        this.contentColumnName = builder.contentColumnName;
        this.metadataColumnName = builder.metadataColumnName;
        this.distanceType = builder.distanceType;
        this.initializeSchema = builder.initializeSchema;
        this.timeout = builder.timeout;
        this.filterExpressionConverter = new ClickHouseFilterExpressionConverter(this.metadataColumnName);
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    /**
     * Creates a new builder instance for configuring an ClickhouseVectorStore.
     *
     * @return A new ClickhouseVectorStore.Builder instance
     */
    public static Builder builder(Client client, EmbeddingModel embeddingModel) {
        return new Builder(client, embeddingModel);
    }

    @Override
    public void doAdd(List<Document> documents) {
        List<float[]> embeddings = this.embeddingModel.embed(
                documents, EmbeddingOptionsBuilder.builder().build(), this.batchingStrategy);

        List<Map<String, Object>> dtos = new ArrayList<>();
        for (Document document : documents) {
            Map<String, Object> dto = Map.of(
                    this.idColumnName, document.getId(),
                    this.embeddingColumnName, EmbeddingUtils.toList(embeddings.get(documents.indexOf(document))),
                    this.contentColumnName, document.getText(),
                    this.metadataColumnName, document.getMetadata());
            dtos.add(dto);
        }

        byte[] json = toJson(dtos);
        InputStream inputStream = new ByteArrayInputStream(json);

        try (InsertResponse response = client.insert(this.getFullTableName(), inputStream, ClickHouseFormat.JSON)
                .get(timeout, TimeUnit.MILLISECONDS)) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Insert finished: {} rows added",
                        response.getMetrics()
                                .getMetric(ServerMetrics.NUM_ROWS_WRITTEN)
                                .getLong());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to write JSONEachRow data", e);
        }
    }

    private byte[] toJson(List<Map<String, Object>> dataList) {
        try {
            return objectMapper.writeValueAsBytes(dataList);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Conversion from Object to JSON failed", ex);
        }
    }

    @Override
    public void doDelete(List<String> idList) {
        if (idList == null || idList.isEmpty()) {
            return;
        }
        client.execute(String.format(
                "DELETE FROM %s WHERE %s IN (%s)",
                this.getFullTableName(),
                idColumnName,
                idList.stream().map(str -> "'" + str + "'").collect(Collectors.joining(","))));
    }

    @Override
    protected void doDelete(Filter.Expression filterExpression) {
        Assert.notNull(filterExpression, "Filter expression must not be null");

        try {
            String nativeFilterExpression = this.filterExpressionConverter.convertExpression(filterExpression);
            String sql = String.format("DELETE FROM %s WHERE %s", getFullTableName(), nativeFilterExpression);
            logger.debug("Executing delete with filter: {}", sql);
            this.client.execute(sql);
        } catch (Exception e) {
            logger.error("Failed to delete documents by filter: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to delete documents by filter", e);
        }
    }

    @Override
    public List<Document> doSimilaritySearch(SearchRequest request) {
        String searchQuery = this.buildSearchQuery(request);
        float[] embedding = this.embeddingModel.embed(request.getQuery());
        Map<String, Object> sqlQueryParams = Map.of(
                "embedding", Arrays.toString(embedding),
                "similarityThreshold", request.getSimilarityThreshold(),
                "topK", request.getTopK());
        try (var records = client.queryRecords(searchQuery, sqlQueryParams).get(this.timeout, TimeUnit.MILLISECONDS)) {
            List<Document> documents = new ArrayList<>();
            records.forEach(record -> documents.add(mapRecordToDocument(record)));
            return documents;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String buildSearchQuery(SearchRequest request) {
        String filteringClause = "";
        if (request.hasFilterExpression()) {
            filteringClause =
                    "AND (" + this.filterExpressionConverter.convertExpression(request.getFilterExpression()) + ")";
        }
        String queryTemplate =
                """
                WITH {embedding:Array(Float32)} AS reference_vector
                SELECT %s, %s, %s, %s(%s, reference_vector) as %s
                FROM %s
                WHERE distance >= {similarityThreshold:Float64}
                  %s
                ORDER BY distance
                LIMIT {topK:UInt32}
                """;
        return String.format(
                queryTemplate,
                this.idColumnName,
                this.contentColumnName,
                this.metadataColumnName,
                this.distanceType.getFunctionName(),
                this.embeddingColumnName,
                DISTANCE_COLUMN_NAME,
                this.getFullTableName(),
                filteringClause);
    }

    private Document mapRecordToDocument(GenericRecord record) {
        return Document.builder()
                .id(record.getString(this.idColumnName))
                .text(record.getString(this.contentColumnName))
                .metadata(getMetadata(record))
                .score(1.0 - record.getDouble(DISTANCE_COLUMN_NAME))
                .build();
    }

    private Map<String, Object> getMetadata(GenericRecord record) {
        Map<String, Object> filteredMetadata = filter((Map<String, Object>) record.getObject(this.metadataColumnName));
        filteredMetadata.put(DISTANCE_COLUMN_NAME, 1.0 - record.getDouble(DISTANCE_COLUMN_NAME));
        return filteredMetadata;
    }

    private Map<String, Object> filter(Map<String, Object> sourceMap) {
        Map<String, Object> filteredMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
            if (entry.getValue() != null) {
                filteredMap.put(entry.getKey(), entry.getValue());
            }
        }
        return filteredMap;
    }

    @Override
    public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
        return VectorStoreObservationContext.builder(CLICKHOUSE_VECTOR_STORE, operationName)
                .namespace(this.databaseName)
                .collectionName(this.tableName)
                .fieldName(this.embeddingColumnName)
                .dimensions(this.embeddingModel.dimensions())
                .similarityMetric(getSimilarityMetric());
    }

    private String getSimilarityMetric() {
        if (!SIMILARITY_TYPE_MAPPING.containsKey(this.distanceType)) {
            return this.distanceType.name();
        }
        return SIMILARITY_TYPE_MAPPING.get(this.distanceType).value();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.initializeSchema) {
            createDatabase();
            createTable();
        }
    }

    private void createDatabase() {
        client.execute(String.format("CREATE DATABASE IF NOT EXISTS %s", this.databaseName));
    }

    private void createTable() {
        String queryTemplate =
                """
                CREATE TABLE IF NOT EXISTS %s
                (
                        %s String,           -- id
                        %s String,           -- content
                        %s Array(Float64),   -- embedding
                        %s JSON,             -- metadata
                        CONSTRAINT embedding_dimension CHECK length(%s) = %d,
                        INDEX annoy_embedding_idx %s TYPE vector_similarity('hnsw', '%s', %d)
                )
                ENGINE = MergeTree
                ORDER BY %s
                """;

        client.execute(String.format(
                queryTemplate,
                this.getFullTableName(),
                this.idColumnName,
                this.contentColumnName,
                this.embeddingColumnName,
                this.metadataColumnName,
                this.embeddingColumnName,
                this.embeddingModel.dimensions(),
                this.embeddingColumnName,
                this.distanceType.getFunctionName(),
                this.embeddingModel.dimensions(),
                this.idColumnName));
    }

    private String getFullTableName() {
        return StringUtils.hasLength(this.databaseName) ? this.databaseName + "." + this.tableName : this.tableName;
    }

    public enum DistanceType {
        COSINE("cosineDistance"),
        L2("L2Distance");

        private String name;

        DistanceType(String name) {
            this.name = name;
        }

        public String getFunctionName() {
            return name;
        }
    }

    /**
     * Builder for the Clickhouse vector store.
     */
    public static class Builder extends AbstractVectorStoreBuilder<Builder> {

        public long timeout = DEFAULT_TIMEOUT;
        private Client client;
        private ObjectMapper objectMapper = DEFAULT_OBJECT_MAPPER;
        private String databaseName = DEFAULT_DATABASE_NAME;
        private String tableName = DEFAULT_TABLE_NAME;
        private String idColumnName = DEFAULT_ID_COLUMN_NAME;
        private String embeddingColumnName = DEFAULT_EMBEDDING_COLUMN_NAME;
        private String contentColumnName = DEFAULT_CONTENT_COLUMN_NAME;
        private String metadataColumnName = DEFAULT_METADATA_COLUMN_NAME;
        private DistanceType distanceType = DEFAULT_DISTANCE_TYPE;
        private boolean initializeSchema = DEFAULT_INITIALIZE_SCHEMA;

        private Builder(Client client, EmbeddingModel embeddingModel) {
            super(embeddingModel);
            this.client = client;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder idColumnName(String idColumnName) {
            this.idColumnName = idColumnName;
            return this;
        }

        public Builder embeddingColumnName(String embeddingColumnName) {
            this.embeddingColumnName = embeddingColumnName;
            return this;
        }

        public Builder contentColumnName(String contentColumnName) {
            this.contentColumnName = contentColumnName;
            return this;
        }

        public Builder metadataColumnName(String metadataColumnName) {
            this.metadataColumnName = metadataColumnName;
            return this;
        }

        public Builder distanceType(DistanceType distanceType) {
            this.distanceType = distanceType;
            return this;
        }

        public Builder initializeSchema(boolean initializeSchema) {
            this.initializeSchema = initializeSchema;
            return this;
        }

        public Builder timeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        @Override
        public ClickHouseVectorStore build() {
            return new ClickHouseVectorStore(this);
        }
    }
}
