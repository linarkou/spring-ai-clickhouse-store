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

package org.springframework.ai.vectorstore.clickhouse.autoconfigure;

import java.time.Duration;
import org.springframework.ai.vectorstore.clickhouse.ClickHouseVectorStore;
import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for ClickHouse Vector Store.
 *
 * @author Linar Abzaltdinov
 */
@ConfigurationProperties(ClickHouseVectorStoreProperties.CONFIG_PREFIX)
public class ClickHouseVectorStoreProperties extends CommonVectorStoreProperties {

    public static final String CONFIG_PREFIX = "spring.ai.vectorstore.clickhouse";

    private String databaseName = ClickHouseVectorStore.DEFAULT_DATABASE_NAME;

    private String tableName = ClickHouseVectorStore.DEFAULT_TABLE_NAME;

    private String idColumnName = ClickHouseVectorStore.DEFAULT_ID_COLUMN_NAME;

    private String embeddingColumnName = ClickHouseVectorStore.DEFAULT_EMBEDDING_COLUMN_NAME;

    private String contentColumnName = ClickHouseVectorStore.DEFAULT_CONTENT_COLUMN_NAME;

    private String metadataColumnName = ClickHouseVectorStore.DEFAULT_METADATA_COLUMN_NAME;

    private ClickHouseVectorStore.DistanceType distanceType = ClickHouseVectorStore.DEFAULT_DISTANCE_TYPE;

    private Duration requestTimeout = Duration.ofMillis(ClickHouseVectorStore.DEFAULT_TIMEOUT);

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getIdColumnName() {
        return idColumnName;
    }

    public void setIdColumnName(String idColumnName) {
        this.idColumnName = idColumnName;
    }

    public String getEmbeddingColumnName() {
        return embeddingColumnName;
    }

    public void setEmbeddingColumnName(String embeddingColumnName) {
        this.embeddingColumnName = embeddingColumnName;
    }

    public String getContentColumnName() {
        return contentColumnName;
    }

    public void setContentColumnName(String contentColumnName) {
        this.contentColumnName = contentColumnName;
    }

    public String getMetadataColumnName() {
        return metadataColumnName;
    }

    public void setMetadataColumnName(String metadataColumnName) {
        this.metadataColumnName = metadataColumnName;
    }

    public ClickHouseVectorStore.DistanceType getDistanceType() {
        return distanceType;
    }

    public void setDistanceType(ClickHouseVectorStore.DistanceType distanceType) {
        this.distanceType = distanceType;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
