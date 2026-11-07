<div align="center">
  <h1>Spring AI ClickHouse Store</h1>
  <a href="https://www.apache.org/licenses/LICENSE-2.0">
    <img alt="Apache 2.0 License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg">
  </a>
  <a href="https://central.sonatype.com/artifact/io.github.linarkou/spring-ai-starter-vector-store-clickhouse">
    <img alt="Maven Central Version" src="https://img.shields.io/maven-central/v/io.github.linarkou/spring-ai-starter-vector-store-clickhouse">
  </a>
  <a href="https://github.com/linarkou/spring-ai-clickhouse-store/actions/workflows/maven-build.yml">
    <img alt="Build Status" src="https://github.com/linarkou/spring-ai-clickhouse-store/actions/workflows/maven-build.yml/badge.svg">
  </a>
</div>

This document walks you through setting up `ClickHouseVectorStore`
to store document embeddings and perform similarity searches.

## What is ClickHouse
[ClickHouse](https://clickhouse.com/) is a high-performance, column-oriented SQL database management system (DBMS)
for online analytical processing (OLAP) that allows users to generate analytical reports using SQL queries in real-time.
It is available as both an open-source software and a cloud offering.

## Compatibility

This branch targets **Spring AI 2.0.2**, **Spring Boot 4.1.2**, and **Java 17+**.
It uses **ClickHouse Java Client v2 0.11.0**, including support for ZSTD-compressed server responses.
The implementation follows the core store, auto-configuration, and starter separation
used by the [Spring AI vector store modules](https://github.com/spring-projects/spring-ai/tree/v2.0.2/vector-stores).
The starter also includes Spring AI's vector store observation auto-configuration.

## Auto-configuration

To enable Spring Boot auto-configuration for the ClickHouse Vector Store,
add the following dependency to your project's Maven `pom.xml` file:

```xml
<dependency>
    <groupId>io.github.linarkou</groupId>
    <artifactId>spring-ai-starter-vector-store-clickhouse</artifactId>
</dependency>
```

or to your Gradle `build.gradle` build file.

```groovy
dependencies {
    implementation 'io.github.linarkou:spring-ai-starter-vector-store-clickhouse'
}
```

Additionally, you will need a configured `EmbeddingModel` bean.
Refer to the [Spring AI Embedding Models](https://docs.spring.io/spring-ai/reference/api/embeddings.html#available-implementations) documentation for more information.

Now you can auto-wire the `ClickHouseVectorStore` as a vector store in your application.

```java
@Autowired VectorStore vectorStore;

// ...

List<Document> documents = List.of(
new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1")),
new Document("The World is Big and Salvation Lurks Around the Corner"),
new Document("You walk forward facing the past and you turn back toward the future.", Map.of("meta2", "meta2")));

// Add the documents to ClickHouse
vectorStore.add(documents);

// Retrieve documents similar to a query
List<Document> results = vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());
```

## Metadata Filtering

You can leverage the generic, portable [metadata filters](https://docs.spring.io/spring-ai/reference/api/vectordbs.html#metadata-filters) with ClickHouse as well.

For example, you can use either the text expression language:

```java
vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("The World")
        .topK(TOP_K)
        .similarityThreshold(SIMILARITY_THRESHOLD)
        .filterExpression("author != 'john' && article_type == 'blog'").build());
```

or programmatically using the `Filter.Expression` DSL:

```java
FilterExpressionBuilder b = new FilterExpressionBuilder();

vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("The World")
        .topK(TOP_K)
        .similarityThreshold(SIMILARITY_THRESHOLD)
        .filterExpression(b.and(
            b.ne("author", "john"),
            b.eq("article_type", "blog")).build()).build());
```

Search results expose the similarity as `Document.getScore()` and the raw distance
as `Document.getMetadata().get("distance")`, following Spring AI's vector store convention.
The similarity score is `1 - distance` for both supported distance functions.
Positive `similarityThreshold` values select documents with a score greater than or equal to the threshold.
`similarityThresholdAll()` disables the threshold, including for L2 distances greater than one.
When upgrading from 2.0.0, note that `distance` previously contained the similarity score
and the threshold comparison was reversed. Applications reading that metadata should
use `Document.getScore()` if they need the similarity.

For more examples, see [test ClickHouseVectorStoreIT.testSimilaritySearchWithFilters()](./spring-ai-clickhouse-store/src/test/java/org/springaicommunity/ai/vectorstore/clickhouse/ClickHouseVectorStoreIT.java).

## Configuration Properties

To connect to ClickHouse and use the `ClickHouseVectorStore`, you need to provide access details for your instance.
A simple configuration can be provided via Spring Boot's `application.yml`:

```yaml
spring:
  ai:
    vectorstore:
      clickhouse:
        client:
          endpoints:
            - <clickhouse url>
          username: <db username>
          password: <db password>
        database-name: ai
        table-name: vector_store
        initialize-schema: true
```

Properties starting with `spring.ai.vectorstore.clickhouse.*` are used to configure the `ClickHouseVectorStore`:

| Property                                                             | Description                                                                       | Default Value |
|----------------------------------------------------------------------|-----------------------------------------------------------------------------------|---------------|
| `spring.ai.vectorstore.clickhouse.database-name`                     | The name of the database to use                                                   | `ai`
| `spring.ai.vectorstore.clickhouse.table-name`                        | Table name to store the vectors                                                   | `vector_store`
| `spring.ai.vectorstore.clickhouse.id-column-name`                    | The ID column name for the table                                                  | `id`
| `spring.ai.vectorstore.clickhouse.content-column-name`               | The content column name for the table                                             | `content`
| `spring.ai.vectorstore.clickhouse.embedding-column-name`             | The embedding column name for the table                                           | `embedding`
| `spring.ai.vectorstore.clickhouse.distance-type`                     | The distance type to be used for vector similarity search                         | `COSINE`
| `spring.ai.vectorstore.clickhouse.initialize-schema`                 | Create the database and vector table on startup                                   | `false`
| `spring.ai.vectorstore.clickhouse.request-timeout`                   | Timeout for all requests (used in CompletableFuture::get)                         | `10s`
| `spring.ai.vectorstore.clickhouse.client.endpoints`                  | Uris of CLickHouse instances                                                      | -
| `spring.ai.vectorstore.clickhouse.client.username`                   | Username for authentication with server                                           | -
| `spring.ai.vectorstore.clickhouse.client.password`                   | Password for authentication with server                                           | -
| `spring.ai.vectorstore.clickhouse.client.access-token`               | Full Authorization header value, including the scheme (for example, `Bearer <token>`) | -
| `spring.ai.vectorstore.clickhouse.client.default-database-name`      | The default database name that will be used by operations if not specified        | -
| `spring.ai.vectorstore.clickhouse.client.ssl-authentication`         | Whether to use SSL Client Certificate to authenticate with server                 | -
| `spring.ai.vectorstore.clickhouse.client.ssl-trust-store-path`       | Path to the trust store file. Cannot be combined with certificates                | -
| `spring.ai.vectorstore.clickhouse.client.ssl-trust-store-password`   | Password for the SSL Trust Store                                                  | -
| `spring.ai.vectorstore.clickhouse.client.ssl-trust-store-type`       | Type of the SSL Trust Store (JKS / PKCS12)                                        | -
| `spring.ai.vectorstore.clickhouse.client.root-certificate-path`      | Path to the key store file                                                        | -
| `spring.ai.vectorstore.clickhouse.client.client-certificate-path`    | Client certificate for mTLS                                                       | -
| `spring.ai.vectorstore.clickhouse.client.client-key-path`            | Client key for mTLS                                                               | -
| `spring.ai.vectorstore.clickhouse.client.connect-timeout`            | Timeout to establish a connection                                                 | - (Defaults set by clickhouse-client)
| `spring.ai.vectorstore.clickhouse.client.connection-request-timeout` | Timeout for waiting a free connection from a pool when all connections are leased | - (Defaults set by clickhouse-client)
| `spring.ai.vectorstore.clickhouse.client.connection-ttl`             | How long any connection would be considered as active and able for a lease        | - (Defaults set by clickhouse-client)
| `spring.ai.vectorstore.clickhouse.client.socket-timeout`             | Timeout to read and write operations                                              | - (Defaults set by clickhouse-client)
| `spring.ai.vectorstore.clickhouse.client.execution-timeout`          | Maximum time for operation to complete                                            | - (Defaults set by clickhouse-client)
| `spring.ai.vectorstore.clickhouse.client.use-meter-registry`         | Whether to inject MeterRegistry for registering client metrics                    | true
| `spring.ai.vectorstore.clickhouse.client.metrics-group-name`         | Name of metrics group (prefix)                                                    | clickhouse-client-metrics
| `spring.ai.vectorstore.clickhouse.client.http-headers`               | List of headers that should be sent with each request                             | []
| `spring.ai.vectorstore.clickhouse.client.server-settings`            | List of server settings that should be sent with each request                     | [allow_experimental_vector_similarity_index=1]
| `spring.ai.vectorstore.clickhouse.client.options`                    | Any other configuration options can be set inside this map                        | []

## Accessing the Native Client

The ClickHouse Vector Store auto-configuration provides access to the underlying native ClickHouse client v2 (`Client`)
through the `VectorStore` interface, as with Spring AI's built-in vector stores:

```java
Client chClient = vectorStore.<Client>getNativeClient().orElseThrow();
```

The native client gives you access to ClickHouse-specific features and operations
that might not be exposed through the `VectorStore` interface.
The auto-configured `Client` is also available as a Spring bean.

## Building and testing

Run unit tests and build all modules with `mvn clean verify`.
Run the ClickHouse integration tests with `mvn clean verify -P integration-tests`.
Integration tests require Docker and download the local Transformers embedding model on first use.
