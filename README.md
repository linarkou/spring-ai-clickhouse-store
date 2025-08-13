# ClickHouse 

This document walks you through setting up `ClickHouseVectorStore`
to store document embeddings and perform similarity searches.

## What is ClickHouse
[ClickHouse](https://clickhouse.com/) is a high-performance, column-oriented SQL database management system (DBMS)
for online analytical processing (OLAP) that allows users to generate analytical reports using SQL queries in real-time.
It is available as both an open-source software and a cloud offering.

## Auto-configuration

To enable Spring Boot auto-configuration for the ClickHouse Vector Store,
add the following dependency to your project's Maven `pom.xml` file:

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-starter-vector-store-clickhouse</artifactId>
</dependency>
```

or to your Gradle `build.gradle` build file.

```groovy
dependencies {
    implementation 'org.springaicommunity:spring-ai-starter-vector-store-clickhouse'
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

// Add the documents to Qdrant
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
        .filterExpression("author != 'john' && 'article_type' == 'blog'").build());
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

For more examples, see [test ClickHouseVectorStoreIT.testSimilaritySearchWithFilters()](./src/test/java/org/springframework/ai/vectorstore/clickhouse/ClickHouseVectorStoreIT.java)

## Configuration Properties

To connect to Qdrant and use the `ClickHouseVectorStore`, you need to provide access details for your instance.
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
| `spring.ai.vectorstore.clickhouse.request-timeout`                   | Timeout for all requests (used in CompletableFuture::get)                         | `10s`
| `spring.ai.vectorstore.clickhouse.client.endpoints`                  | Uris of CLickHouse instances                                                      | -
| `spring.ai.vectorstore.clickhouse.client.username`                   | Username for authentication with server                                           | -
| `spring.ai.vectorstore.clickhouse.client.password`                   | Password for authentication with server                                           | -
| `spring.ai.vectorstore.clickhouse.client.access-token`               | Access token for authentication with server                                       | -
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
through the preconfigured-bean:

```java
com.clickhouse.client.api.Client chClient = context.getBean(com.clickhouse.client.api.Client.class);
```

The native client gives you access to ClickHouse-specific features and operations
that might not be exposed through the `VectorStore` interface.
