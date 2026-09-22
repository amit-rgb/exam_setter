package com.exam.setter.service;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces bounded chunks while preserving paragraph and heading boundaries.
 * The chunk text remains the original source text; structural context is
 * stored as metadata so the embedding model can include it when configured
 * with metadata-mode=EMBED.
 */
@Service
public class StructureAwareChunker {

    public List<Document> chunk(List<Document> documents, int maxChars, int overlapChars, String chunkVersion) {
        List<Document> result = new ArrayList<>();
        int safeMax = Math.max(1200, maxChars);
        int safeOverlap = Math.max(0, Math.min(overlapChars, safeMax / 3));

        for (Document source : documents) {
            if (source == null || source.getText() == null || source.getText().isBlank()) continue;
            result.addAll(chunkDocument(source, safeMax, safeOverlap, chunkVersion));
        }
        return result;
    }

    private List<Document> chunkDocument(Document source, int maxChars, int overlapChars, String chunkVersion) {
        List<Document> result = new ArrayList<>();
        String[] lines = source.getText().replace("\r", "").split("\n");
        String currentSection = String.valueOf(source.getMetadata().getOrDefault("sectionTitle", ""));
        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                flushParagraph(paragraphs, paragraph);
                continue;
            }
            if (isHeading(line)) {
                flushParagraph(paragraphs, paragraph);
                currentSection = cleanHeading(line);
                continue;
            }
            paragraph.append(line).append('\n');
        }
        flushParagraph(paragraphs, paragraph);

        if (paragraphs.isEmpty()) paragraphs.add(source.getText().trim());

        StringBuilder buffer = new StringBuilder();
        String sectionForBuffer = currentSection;
        int localIndex = 0;

        for (String paragraphText : paragraphs) {
            String paragraphSection = extractSection(paragraphText);
            if (!paragraphSection.isBlank()) sectionForBuffer = paragraphSection;

            if (buffer.length() > 0 && buffer.length() + paragraphText.length() + 1 > maxChars) {
                result.add(build(source, buffer.toString().trim(), sectionForBuffer, localIndex++, chunkVersion));
                String overlap = tail(buffer.toString(), overlapChars);
                buffer.setLength(0);
                if (!overlap.isBlank()) buffer.append(overlap).append('\n');
            }
            if (paragraphText.length() > maxChars) {
                if (buffer.length() > 0) {
                    result.add(build(source, buffer.toString().trim(), sectionForBuffer, localIndex++, chunkVersion));
                    buffer.setLength(0);
                }
                for (String piece : hardSplit(paragraphText, maxChars)) {
                    result.add(build(source, piece, sectionForBuffer, localIndex++, chunkVersion));
                }
            } else {
                buffer.append(paragraphText).append('\n');
            }
        }

        if (!buffer.toString().isBlank()) {
            result.add(build(source, buffer.toString().trim(), sectionForBuffer, localIndex, chunkVersion));
        }
        return result;
    }

    private Document build(Document source, String text, String section, int index, String version) {
        Map<String, Object> metadata = new HashMap<>(source.getMetadata());
        if (!section.isBlank()) metadata.put("sectionTitle", section);
        metadata.put("chunkVersion", version);
        metadata.put("chunkLocalIndex", index);
        metadata.put("contentScope", detectScope(text, section));
        return new Document(text, metadata);
    }

    private String detectScope(String text, String section) {
        if (looksLikeTable(text)) return "TABLE";
        if (!section.isBlank()) return "SECTION";
        return "PARAGRAPH";
    }

    private boolean looksLikeTable(String text) {
        String[] lines = text.split("\n");
        int tableLike = 0;
        for (String line : lines) {
            if (line.contains("|") || line.matches(".*\\S\\s{3,}\\S.*")) tableLike++;
        }
        return tableLike >= 2;
    }

    private boolean isHeading(String line) {
        if (line.length() > 120 || line.endsWith(".") || line.endsWith(",")) return false;
        if (line.matches("^(?:\\d+(?:\\.\\d+)*|[A-Z]|[IVX]+)[\\).:]?\\s+.+")) return true;
        if (line.endsWith(":")) return true;
        long letters = line.chars().filter(Character::isLetter).count();
        long upper = line.chars().filter(Character::isUpperCase).count();
        return letters >= 4 && upper >= Math.max(2, letters * 0.65);
    }

    private String cleanHeading(String line) {
        return line.replaceAll("\\s+", " ").trim();
    }

    private String extractSection(String paragraph) {
        return "";
    }

    private void flushParagraph(List<String> paragraphs, StringBuilder paragraph) {
        if (!paragraph.toString().isBlank()) {
            paragraphs.add(paragraph.toString().replaceAll("\\s+", " ").trim());
            paragraph.setLength(0);
        }
    }

    private String tail(String text, int chars) {
        if (chars <= 0 || text.length() <= chars) return text;
        int start = text.length() - chars;
        int boundary = text.indexOf(' ', start);
        return text.substring(boundary > 0 ? boundary + 1 : start);
    }

    private List<String> hardSplit(String text, int maxChars) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maxChars);
            if (end < text.length()) {
                int boundary = text.lastIndexOf(' ', end);
                if (boundary > start + maxChars / 2) end = boundary;
            }
            pieces.add(text.substring(start, end).trim());
            start = end;
        }
        return pieces;
    }
}
