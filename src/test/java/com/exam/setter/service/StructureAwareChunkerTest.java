package com.exam.setter.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructureAwareChunkerTest {

    private final StructureAwareChunker chunker = new StructureAwareChunker();

    @Test
    void preservesHeadingsAsSectionMetadata() {
        List<Document> chunks = chunker.chunk(
                List.of(new Document("1. Matter\n\nMatter is anything that has mass.\n\nProperties of Matter\n\nDensity is mass per unit volume.")),
                1200, 100, "v2");

        assertFalse(chunks.isEmpty());
        assertEquals("Properties of Matter", chunks.get(chunks.size() - 1).getMetadata().get("sectionTitle"));
        assertEquals("SECTION", chunks.get(chunks.size() - 1).getMetadata().get("contentScope"));
    }

    @Test
    void keepsTableLikeContentTogetherWhenPossible() {
        String table = "Name    Formula\nWater   H2O\nCarbon dioxide   CO2\nOxygen   O2";
        List<Document> chunks = chunker.chunk(List.of(new Document(table, Map.of("pageNumber", 4))), 1200, 100, "v2");

        assertEquals(1, chunks.size());
        assertEquals("TABLE", chunks.get(0).getMetadata().get("contentScope"));
        assertEquals(4, chunks.get(0).getMetadata().get("pageNumber"));
    }

    @Test
    void boundsLargeParagraphs() {
        String text = "word ".repeat(1500);
        List<Document> chunks = chunker.chunk(List.of(new Document(text)), 1200, 100, "v2");

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(d -> d.getText().length() <= 1200));
    }
}
