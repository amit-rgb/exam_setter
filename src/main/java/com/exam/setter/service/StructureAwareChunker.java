package com.exam.setter.service;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        List<Paragraph> paragraphs = parseParagraphs(source.getText(),
                String.valueOf(source.getMetadata().getOrDefault("sectionTitle", "")));
        if (paragraphs.isEmpty()) paragraphs = List.of(new Paragraph("", source.getText().trim()));

        List<Document> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String section = "";
        int index = 0;

        for (Paragraph paragraph : paragraphs) {
            if (buffer.length() > 0 && buffer.length() + paragraph.text().length() + 1 > maxChars) {
                result.add(build(source, buffer.toString().trim(), section, index++, chunkVersion));
                String overlap = tail(buffer.toString(), overlapChars);
                buffer.setLength(0);
                if (!overlap.isBlank()) buffer.append(overlap).append('\n');
            }

            if (paragraph.text().length() > maxChars) {
                if (buffer.length() > 0) {
                    result.add(build(source, buffer.toString().trim(), section, index++, chunkVersion));
                    buffer.setLength(0);
                }
                for (String piece : hardSplit(paragraph.text(), maxChars)) {
                    result.add(build(source, piece, paragraph.section(), index++, chunkVersion));
                }
                section = paragraph.section();
            } else {
                if (!paragraph.section().isBlank()) section = paragraph.section();
                buffer.append(paragraph.text()).append('\n');
            }
        }

        if (!buffer.toString().isBlank()) {
            result.add(build(source, buffer.toString().trim(), section, index, chunkVersion));
        }
        return result;
    }

    private List<Paragraph> parseParagraphs(String text, String initialSection) {
        List<Paragraph> result = new ArrayList<>();
        String currentSection = initialSection == null ? "" : initialSection.trim();
        StringBuilder paragraph = new StringBuilder();

        for (String raw : text.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.isBlank()) {
                flush(result, paragraph, currentSection);
                continue;
            }
            if (isHeading(line)) {
                flush(result, paragraph, currentSection);
                currentSection = cleanHeading(line);
                continue;
            }
            paragraph.append(line).append(' ');
        }
        flush(result, paragraph, currentSection);
        return result;
    }

    private void flush(List<Paragraph> result, StringBuilder paragraph, String section) {
        String text = paragraph.toString().replaceAll("\\s+", " ").trim();
        if (!text.isBlank()) result.add(new Paragraph(section, text));
        paragraph.setLength(0);
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
        int tableLike = 0;
        for (String line : text.split("\n")) {
            if (line.contains("|") || line.matches(".*\\S\\s{3,}\\S.*")) tableLike++;
        }
        return tableLike >= 2;
    }

    private boolean isHeading(String line) {
        if (line.length() > 120 || line.endsWith(".") || line.endsWith(",")) return false;
        if (line.matches("^(?:\\d+(?:\\.\\d+)*|[A-Z]|[IVX]+)[\\).:]?\\s+.+")) return true;
        if (line.endsWith(":")) return true;
        if (line.matches("(?:[A-Z][a-z]+\\s+){1,}[A-Z][a-z]+")) return true;
        long letters = line.chars().filter(Character::isLetter).count();
        long upper = line.chars().filter(Character::isUpperCase).count();
        return letters >= 4 && upper >= Math.max(2, letters * 0.65);
    }

    private String cleanHeading(String line) {
        return line.replaceAll("\\s+", " ").trim();
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

    private record Paragraph(String section, String text) {}
}
