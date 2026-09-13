package com.exam.setter.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NcertRetrievalServiceTest {
    @Test
    void retrievesOnlyTheRequestedNcertScope() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("ncert-1", "Thermodynamics", java.util.Map.of("documentKey", "NCERT-11-CHEM-CH06", "bookCode", "kech1", "chapterNumber", 6, "classLevel", "CLASS_11"))));

        NcertRetrievalService service = new NcertRetrievalService(store, "2026");
        List<Document> result = service.retrieve("chemistry", List.of("CLASS_11"), "enthalpy and thermodynamics", "2026", "kech1", 6, 5);

        assertThat(result).hasSize(1);
        verify(store).similaritySearch(argThat(request -> {
            String filter = String.valueOf(request.getFilterExpression());
            return filter.contains("source") && filter.contains("NCERT") && filter.contains("TEXTBOOK")
                    && filter.contains("CHEMISTRY") && filter.contains("2026") && filter.contains("CLASS_11")
                    && filter.contains("kech1") && filter.contains("6");
        }));
    }
}
