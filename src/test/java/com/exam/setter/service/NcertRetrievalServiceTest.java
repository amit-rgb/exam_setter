package com.exam.setter.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NcertRetrievalServiceTest {
    @Test
    void retrievesOnlyTheRequestedNcertScope() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(org.mockito.ArgumentMatchers.any(SearchRequest.class))).thenReturn(List.of(
                new Document("ncert-1", "Thermodynamics", java.util.Map.of("documentKey", "NCERT-11-CHEM-CH06", "bookCode", "kech1", "chapterNumber", 6, "classLevel", "CLASS_11"))));

        NcertRetrievalService service = new NcertRetrievalService(store, "2026");
        List<Document> result = service.retrieve("chemistry", List.of("CLASS_11"), "enthalpy and thermodynamics", "2026", "kech1", 6, 5);

        assertThat(result).hasSize(1);

        org.mockito.ArgumentCaptor<SearchRequest> captor = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        verify(store).similaritySearch(captor.capture());

        String filter = String.valueOf(captor.getValue().getFilterExpression());
        assertThat(filter)
                .contains("source", "NCERT", "TEXTBOOK", "CHEMISTRY", "2026", "CLASS_11", "kech1", "6");
    }
}
