package org.springaicommunity.ai.vectorstore.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.client.api.query.Records;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class ClickHouseVectorStoreTest {

    private final Client client = mock(Client.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

    @Test
    void exposesNativeClientThroughVectorStoreInterface() {
        VectorStore store =
                ClickHouseVectorStore.builder(client, embeddingModel).build();

        assertThat(store.<Client>getNativeClient()).containsSame(client);
    }

    @Test
    void returnsRawDistanceInMetadataAndSimilarityAsScore() throws Exception {
        var record = mock(GenericRecord.class);
        when(record.getString("id")).thenReturn("doc-1");
        when(record.getString("content")).thenReturn("document");
        when(record.getObject("metadata")).thenReturn(Map.of("source", "test"));
        when(record.getDouble("distance")).thenReturn(0.25);
        var records = mock(Records.class);
        doAnswer(invocation -> {
                    Consumer<GenericRecord> consumer = invocation.getArgument(0);
                    consumer.accept(record);
                    return null;
                })
                .when(records)
                .forEach(any());
        when(client.queryRecords(anyString(), anyMap())).thenReturn(CompletableFuture.completedFuture(records));
        when(embeddingModel.embed("query")).thenReturn(new float[] {1, 0});
        var store = ClickHouseVectorStore.builder(client, embeddingModel).build();

        List<Document> results = store.similaritySearch("query");

        assertThat(results).singleElement().satisfies(document -> {
            assertThat(document.getId()).isEqualTo("doc-1");
            assertThat(document.getScore()).isEqualTo(0.75);
            assertThat(document.getMetadata())
                    .containsEntry("source", "test")
                    .containsEntry(DocumentMetadata.DISTANCE.value(), 0.25);
        });
        verify(records).close();
    }

    @Test
    void usesCallerSuppliedQueryVectorAndSearchOptions() {
        var records = mock(Records.class);
        when(client.queryRecords(anyString(), anyMap())).thenReturn(CompletableFuture.completedFuture(records));
        VectorStore store = ClickHouseVectorStore.builder(client, embeddingModel)
                .dimensions(2)
                .build();

        store.similaritySearch(
                new float[] {0, 1},
                SearchRequest.builder()
                        .query("ignored")
                        .topK(2)
                        .similarityThreshold(0.5)
                        .filterExpression("source == 'test'")
                        .build());

        verify(client)
                .queryRecords(
                        contains("metadata"),
                        eq(Map.of("embedding", "[0.0, 1.0]", "topK", 2, "similarityThreshold", 0.5)));
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void rejectsIncorrectDimensionsBeforeWritingOrSearching() {
        VectorStore store = ClickHouseVectorStore.builder(client, embeddingModel)
                .upsertEnabled(true)
                .dimensions(2)
                .build();
        var correct = new EmbeddedDocument(new Document("valid"), new float[] {1, 0});
        var wrong = new EmbeddedDocument(new Document("invalid"), new float[] {1});

        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.upsert(List.of(correct, wrong)))
                .withMessageContaining("expects dimension 2");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.similaritySearch(
                        new float[] {1}, SearchRequest.builder().build()))
                .withMessageContaining("expects dimension 2");
        verifyNoInteractions(client, embeddingModel);
    }

    @Test
    void leavesLegacyTableWritesOptIn() {
        VectorStore store =
                ClickHouseVectorStore.builder(client, embeddingModel).build();

        store.upsert(List.of());
        assertThatThrownBy(() -> store.upsert(List.of(new EmbeddedDocument(new Document("text"), new float[] {1}))))
                .isInstanceOf(UnsupportedOperationException.class);
        verifyNoInteractions(client, embeddingModel);
    }
}
