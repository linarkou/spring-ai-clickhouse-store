/*
 * Copyright 2023-2025 the original author or authors.
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

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.metrics.ServerMetrics;
import com.clickhouse.data.ClickHouseFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

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
public class ClickhouseVectorStore extends AbstractObservationVectorStore implements InitializingBean, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ClickhouseVectorStore.class);
    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_DATABASE_NAME = "ai";
    private static final String DEFAULT_TABLE_NAME = "vector_store";
    private static final String DEFAULT_ID_COLUMN_NAME = "id";
    private static final String DEFAULT_EMBEDDING_COLUMN_NAME = "embedding";
    private static final String DEFAULT_CONTENT_COLUMN_NAME = "content";
    private static final String DEFAULT_METADATA_COLUMN_NAME = "metadata";
    private static final Similarity DEFAULT_SIMILARITY_TYPE = Similarity.COSINE;
    private static final boolean DEFAULT_INITIALIZE_SCHEMA = false;
    private static final long DEFAULT_TIMEOUT = 10_000L; // milliseconds

    private static final Map<Similarity, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(
            Similarity.COSINE, VectorStoreSimilarityMetric.COSINE,
            Similarity.L2, VectorStoreSimilarityMetric.EUCLIDEAN);

    private final Client client;

    private final ObjectMapper objectMapper;

    private String databaseName;
    private String tableName;
    private String idColumnName;
    private String embeddingColumnName;
    private String contentColumnName;
    private String metadataColumnName;
    private Similarity similarity;
    private boolean initializeSchema;
    private long timeout;

    protected ClickhouseVectorStore(Builder builder) {
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
        this.similarity = builder.similarity;
        this.initializeSchema = builder.initializeSchema;
        this.timeout = builder.timeout;
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
                    idColumnName, document.getId(),
                    embeddingColumnName, EmbeddingUtils.toList(embeddings.get(documents.indexOf(document))),
                    contentColumnName, document.getText());
            dtos.add(dto);
        }

        byte[] json = toJson(dtos);
        InputStream inputStream = new ByteArrayInputStream(json);

        try (InsertResponse response =
                client.insert(tableName, inputStream, ClickHouseFormat.JSON).get(timeout, TimeUnit.MILLISECONDS)) {
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

    @Override
    public void doDelete(List<String> idList) {
        client.execute(String.format(
                "DELETE FROM %s.%s WHERE %s IN (%s)",
                databaseName, tableName, idColumnName, String.join(", ", idList)));
    }

    @Override
    public List<Document> doSimilaritySearch(SearchRequest request) {
        // TODO implement
        return null;
    }

    @Override
    public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
        return VectorStoreObservationContext.builder("CLICKHOUSE", operationName)
                .namespace(this.databaseName)
                .collectionName(this.tableName)
                .fieldName(this.embeddingColumnName)
                .dimensions(this.embeddingModel.dimensions())
                .similarityMetric(getSimilarityMetric());
    }

    private String getSimilarityMetric() {
        if (!SIMILARITY_TYPE_MAPPING.containsKey(this.similarity)) {
            return this.similarity.name();
        }
        return SIMILARITY_TYPE_MAPPING.get(this.similarity).value();
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
                CREATE TABLE IF NOT EXISTS %s.%s
                (
                        %s String,           -- id
                        %s Nullable(String), -- content
                        %s Array(Float64),   -- embedding
                        %s JSON              -- metadata
                        CONSTRAINT embedding_dimension CHECK length(%s) = %d,"
                        INDEX annoy_embedding_idx %s TYPE vector_similarity('hnsw', '%s', %d)"
                )
                ENGINE = MergeTree
                ORDER BY id
                """;

        client.execute(String.format(
                "queryTemplate",
                this.databaseName,
                this.tableName,
                this.idColumnName,
                this.contentColumnName,
                this.embeddingColumnName,
                this.metadataColumnName,
                this.embeddingColumnName,
                this.embeddingModel.dimensions(),
                this.embeddingColumnName,
                this.similarity.getName(),
                this.embeddingModel.dimensions()));
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    private byte[] toJson(List<Map<String, Object>> dataList) {
        try {
            return objectMapper.writeValueAsBytes(dataList);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Conversion from Object to JSON failed", ex);
        }
    }

    public enum Similarity {
        COSINE("cosineDistance"),
        L2("L2Distance");

        private String name;

        Similarity(String name) {
            this.name = name;
        }

        public String getName() {
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
        private Similarity similarity = DEFAULT_SIMILARITY_TYPE;
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

        public Builder similarity(Similarity similarity) {
            this.similarity = similarity;
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
        public ClickhouseVectorStore build() {
            return new ClickhouseVectorStore(this);
        }
    }
}
