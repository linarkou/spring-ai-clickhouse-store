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

package org.springaicommunity.ai.vectorstore.clickhouse;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.command.CommandSettings;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.insert.InsertSettings;
import com.clickhouse.client.api.metrics.ServerMetrics;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.data.ClickHouseFormat;
import com.clickhouse.data.ClickHouseValues;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL)
                    .withContentInclusion(JsonInclude.Include.NON_NULL))
            .build();
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
    private final boolean upsertEnabled;
    private volatile boolean upsertTableValidated;
    private int dimensions;
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
        this.upsertEnabled = builder.upsertEnabled;
        this.dimensions = builder.dimensions;
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
    public <T> Optional<T> getNativeClient() {
        @SuppressWarnings("unchecked")
        T nativeClient = (T) this.client;
        return Optional.of(nativeClient);
    }

    @Override
    public void doAdd(List<Document> documents) {
        List<float[]> embeddings =
                this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(), this.batchingStrategy);

        List<Map<String, Object>> rows = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            rows.add(toRow(documents.get(i), embeddings.get(i)));
        }
        insertRows(rows);
    }

    @Override
    protected boolean supportsUpsert() {
        return this.upsertEnabled;
    }

    @Override
    protected void doUpsert(List<EmbeddedDocument> entries) {
        int expected = requiredDimensions();
        Map<String, Map<String, Object>> rowsById = new LinkedHashMap<>();
        for (EmbeddedDocument entry : entries) {
            Document document = entry.document();
            float[] embedding = entry.embedding();
            checkDimensions("Embedding for document " + document.getId(), embedding.length, expected);
            rowsById.put(document.getId(), toRow(document, embedding));
        }
        insertRows(new ArrayList<>(rowsById.values()));
    }

    private Map<String, Object> toRow(Document document, float[] embedding) {
        return Map.of(
                this.idColumnName, document.getId(),
                this.embeddingColumnName, EmbeddingUtils.toList(embedding),
                this.contentColumnName, Objects.requireNonNullElse(document.getText(), ""),
                this.metadataColumnName, document.getMetadata());
    }

    private void insertRows(List<Map<String, Object>> rows) {
        validateUpsertTable();
        InputStream inputStream = new ByteArrayInputStream(toJson(rows));
        InsertSettings settings = new InsertSettings();
        if (this.upsertEnabled) {
            settings.serverSetting("async_insert", "0").serverSetting("insert_deduplicate", "0");
        }

        try (InsertResponse response = client.insert(
                        this.getFullTableName(), inputStream, ClickHouseFormat.JSON, settings)
                .get(timeout, TimeUnit.MILLISECONDS)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Insert finished: "
                        + response.getMetrics()
                                .getMetric(ServerMetrics.NUM_ROWS_WRITTEN)
                                .getLong()
                        + " rows added");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while inserting documents", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to insert documents into ClickHouse", e);
        }
    }

    private byte[] toJson(List<Map<String, Object>> dataList) {
        try {
            return objectMapper.writeValueAsBytes(dataList);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Conversion from Object to JSON failed", ex);
        }
    }

    @Override
    public void doDelete(List<String> idList) {
        if (idList == null || idList.isEmpty()) {
            return;
        }
        validateUpsertTable();
        String ids =
                idList.stream().map(ClickHouseValues::convertToQuotedString).collect(Collectors.joining(","));
        execute(
                "DELETE FROM %s WHERE %s IN (%s)".formatted(getFullTableName(), this.idColumnName, ids),
                new CommandSettings());
    }

    @Override
    protected void doDelete(Filter.Expression filterExpression) {
        Assert.notNull(filterExpression, "Filter expression must not be null");

        String predicate = this.filterExpressionConverter.convertExpression(filterExpression);
        CommandSettings settings = new CommandSettings();
        if (this.upsertEnabled) {
            validateUpsertTable();
            // Freeze the IDs from current versions before the mutation visits individual parts.
            predicate = "has((SELECT groupArray(%1$s) FROM %2$s FINAL WHERE %3$s), %1$s)"
                    .formatted(this.idColumnName, getFullTableName(), predicate);
            settings.serverSetting("mutations_execute_subqueries_on_initiator", "1")
                    .serverSetting("optimize_move_to_prewhere_if_final", "0");
        }
        execute("DELETE FROM %s WHERE %s".formatted(getFullTableName(), predicate), settings);
    }

    @Override
    public List<Document> doSimilaritySearch(SearchRequest request) {
        return search(this.embeddingModel.embed(request.getQuery()), request);
    }

    @Override
    protected boolean supportsQueryEmbeddingSearch() {
        return true;
    }

    @Override
    protected List<Document> doSimilaritySearch(float[] queryEmbedding, SearchRequest request) {
        checkDimensions("Query embedding", queryEmbedding.length, requiredDimensions());
        return search(queryEmbedding, request);
    }

    private List<Document> search(float[] embedding, SearchRequest request) {
        validateUpsertTable();
        String searchQuery = this.buildSearchQuery(request);
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
        String thresholdClause = request.getSimilarityThreshold() == SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL
                ? "1 = 1"
                : "distance <= 1 - {similarityThreshold:Float64}";
        String filteringClause = "";
        if (request.hasFilterExpression()) {
            filteringClause =
                    "AND (" + this.filterExpressionConverter.convertExpression(request.getFilterExpression()) + ")";
        }
        String queryTemplate = """
                WITH {embedding:Array(Float32)} AS reference_vector
                SELECT %s, %s, %s, %s(%s, reference_vector) as %s
                FROM %s%s
                WHERE %s
                  %s
                ORDER BY distance
                LIMIT {topK:UInt32}
                %s
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
                this.upsertEnabled ? " FINAL" : "",
                thresholdClause,
                filteringClause,
                this.upsertEnabled ? "SETTINGS optimize_move_to_prewhere_if_final = 0" : "");
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
        filteredMetadata.put(DocumentMetadata.DISTANCE.value(), record.getDouble(DISTANCE_COLUMN_NAME));
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
        var builder = VectorStoreObservationContext.builder(CLICKHOUSE_VECTOR_STORE, operationName)
                .namespace(this.databaseName)
                .collectionName(this.tableName)
                .fieldName(this.embeddingColumnName)
                .similarityMetric(getSimilarityMetric());
        if (this.dimensions > 0) {
            builder.dimensions(this.dimensions);
        }
        return builder;
    }

    private String getSimilarityMetric() {
        if (!SIMILARITY_TYPE_MAPPING.containsKey(this.distanceType)) {
            return this.distanceType.name();
        }
        return SIMILARITY_TYPE_MAPPING.get(this.distanceType).value();
    }

    private int requiredDimensions() {
        if (this.dimensions <= 0) {
            throw new IllegalStateException(
                    "Configure vector dimensions when using caller-supplied embeddings with a pre-created table");
        }
        return this.dimensions;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.initializeSchema) {
            if (this.dimensions == 0) {
                this.dimensions = this.embeddingModel.dimensions();
            }
            execute("CREATE DATABASE IF NOT EXISTS " + this.databaseName, new CommandSettings());
            createTable();
            validateUpsertTable();
        }
    }

    private void createTable() {
        String vectorIndex = this.upsertEnabled
                ? ""
                : ", INDEX annoy_embedding_idx %s TYPE vector_similarity('hnsw', '%s', %d)"
                        .formatted(this.embeddingColumnName, this.distanceType.getFunctionName(), this.dimensions);
        String sql = """
                CREATE TABLE IF NOT EXISTS %s
                (
                        %s String,
                        %s String,
                        %s Array(Float64),
                        %s JSON,
                        CONSTRAINT embedding_dimension CHECK length(%s) = %d%s
                )
                ENGINE = %s
                ORDER BY %s
                """.formatted(
                        getFullTableName(),
                        this.idColumnName,
                        this.contentColumnName,
                        this.embeddingColumnName,
                        this.metadataColumnName,
                        this.embeddingColumnName,
                        this.dimensions,
                        vectorIndex,
                        this.upsertEnabled ? "ReplacingMergeTree" : "MergeTree",
                        this.idColumnName);
        execute(sql, new CommandSettings());
    }

    private void validateUpsertTable() {
        if (!this.upsertEnabled || this.upsertTableValidated) {
            return;
        }
        String sql =
                """
                SELECT name FROM system.tables
                WHERE database = %s AND name = {table:String}
                  AND engine = 'ReplacingMergeTree' AND sorting_key = {idColumn:String}
                  AND partition_key = ''
                  AND (NOT startsWith(engine_full, 'ReplacingMergeTree(')
                       OR startsWith(engine_full, 'ReplacingMergeTree()'))
                """.formatted(StringUtils.hasLength(this.databaseName) ? "{database:String}" : "currentDatabase()");
        Map<String, Object> params = Map.of(
                "database", Objects.requireNonNullElse(this.databaseName, ""),
                "table", this.tableName,
                "idColumn", this.idColumnName);
        boolean valid;
        try (var records = client.queryRecords(sql, params).get(this.timeout, TimeUnit.MILLISECONDS)) {
            valid = records.iterator().hasNext();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking the upsert table", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inspect the ClickHouse upsert table", e);
        }
        if (!valid) {
            throw new IllegalStateException("Upsert requires an unpartitioned ReplacingMergeTree without a version "
                    + "column, ordered by " + this.idColumnName + ". Check table " + getFullTableName());
        }
        this.upsertTableValidated = true;
    }

    private void execute(String sql, CommandSettings settings) {
        try (var response = client.execute(sql, settings).get(this.timeout, TimeUnit.MILLISECONDS)) {
            // Wait for ClickHouse to complete the command and surface server errors.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing a ClickHouse command", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute a ClickHouse command", e);
        }
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
        private boolean upsertEnabled;
        private int dimensions;

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

        public Builder upsertEnabled(boolean upsertEnabled) {
            this.upsertEnabled = upsertEnabled;
            return this;
        }

        public Builder dimensions(int dimensions) {
            Assert.isTrue(dimensions >= 0, "Dimensions must be non-negative");
            this.dimensions = dimensions;
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
