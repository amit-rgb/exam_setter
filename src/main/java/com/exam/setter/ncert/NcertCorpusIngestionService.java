package com.exam.setter.ncert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class NcertCorpusIngestionService {
    private static final Logger log = LoggerFactory.getLogger(NcertCorpusIngestionService.class);

    private final ObjectMapper objectMapper;
    private final VectorStore vectorStore;
    private final NcertCorpusRepository repository;
    private final Path root;
    private final Path manifestPath;
    private final boolean failFast;
    private final int batchSize;
    private final int chunkSize;
    private final int chunkOverlap;
    private final String chunkVersion;
    private final String defaultCorpusVersion;

    public NcertCorpusIngestionService(
            ObjectMapper objectMapper, VectorStore vectorStore, NcertCorpusRepository repository,
            @Value("${app.ncert.ingestion.root:./NCERT}") String root,
            @Value("${app.ncert.ingestion.manifest:./NCERT/manifest.json}") String manifest,
            @Value("${app.ncert.ingestion.fail-fast:false}") boolean failFast,
            @Value("${app.ncert.ingestion.batch-size:50}") int batchSize,
            @Value("${app.ncert.ingestion.chunk-size:500}") int chunkSize,
            @Value("${app.ncert.ingestion.chunk-overlap:80}") int chunkOverlap,
            @Value("${app.ncert.ingestion.chunk-version:v1}") String chunkVersion,
            @Value("${app.ncert.ingestion.corpus-version:2026}") String defaultCorpusVersion) {
        this.objectMapper = objectMapper;
        this.vectorStore = vectorStore;
        this.repository = repository;
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.manifestPath = Path.of(manifest).toAbsolutePath().normalize();
        this.failFast = failFast;
        this.batchSize = Math.max(1, batchSize);
        this.chunkSize = Math.max(100, chunkSize);
        this.chunkOverlap = Math.max(0, Math.min(chunkOverlap, chunkSize - 1));
        this.chunkVersion = chunkVersion;
        this.defaultCorpusVersion = defaultCorpusVersion;
    }

    public RunSummary ingestManifest() throws IOException {
        if (!Files.isRegularFile(manifestPath)) throw new IOException("NCERT manifest not found: " + manifestPath);
        NcertCorpusManifest manifest = objectMapper.readValue(manifestPath.toFile(), NcertCorpusManifest.class);
        validateManifest(manifest);
        String corpusVersion = blankToDefault(manifest.corpusVersion(), defaultCorpusVersion);
        int completed = 0, skipped = 0, failed = 0, chunks = 0;
        for (NcertCorpusManifest.Entry entry : manifest.entries()) {
            try {
                Outcome outcome = ingestEntry(entry, corpusVersion);
                if (outcome.skipped()) skipped++; else { completed++; chunks += outcome.chunks(); }
            } catch (Exception ex) {
                failed++;
                log.error("NCERT ingestion failed for {}: {}", entry.documentKey(), ex.getMessage(), ex);
                if (failFast) throw ex instanceof IOException io ? io : new IOException(ex);
            }
        }
        return new RunSummary(manifest.entries().size(), completed, skipped, failed, chunks, corpusVersion);
    }

    private void validateManifest(NcertCorpusManifest manifest) throws IOException {
        if (manifest == null || manifest.entries() == null || manifest.entries().isEmpty()) {
            throw new IOException("NCERT manifest contains no entries: " + manifestPath);
        }
        if (manifest.schemaVersion() != null && !"1".equals(manifest.schemaVersion())) {
            throw new IOException("Unsupported NCERT manifest schemaVersion: " + manifest.schemaVersion());
        }
        Set<String> keys = new HashSet<>();
        Set<String> files = new HashSet<>();
        for (NcertCorpusManifest.Entry entry : manifest.entries()) {
            validateEntry(entry);
            if (!keys.add(entry.documentKey())) throw new IOException("Duplicate NCERT documentKey: " + entry.documentKey());
            if (!files.add(entry.fileName().replace('\\', '/'))) throw new IOException("Duplicate NCERT fileName: " + entry.fileName());
        }
    }

    private Outcome ingestEntry(NcertCorpusManifest.Entry entry, String corpusVersion) throws Exception {
        Path pdf = safeResolve(entry.fileName());
        if (!Files.isRegularFile(pdf)) throw new IOException("NCERT PDF not found: " + pdf);
        String actualHash = sha256(pdf);
        NcertCorpusRepository.State previous = repository.find(entry.documentKey()).orElse(null);
        if (previous != null && "COMPLETED".equals(previous.status()) && actualHash.equalsIgnoreCase(previous.contentHash())
                && chunkVersion.equals(previous.chunkVersion())) {
            log.info("Skipping unchanged NCERT document: {}", entry.documentKey());
            return new Outcome(true, previous.chunkCount());
        }
        if (entry.contentHash() != null && !entry.contentHash().isBlank() && !actualHash.equalsIgnoreCase(entry.contentHash())) {
            throw new IOException("Manifest SHA-256 mismatch for " + entry.fileName() + ": expected " + entry.contentHash() + ", actual " + actualHash);
        }
        if (previous != null) deleteExistingChunks(entry.documentKey(), previous.chunkVersion(), previous.chunkCount());
        repository.start(entry, corpusVersion, actualHash, chunkVersion);
        try {
            int count = indexPdf(entry, corpusVersion, actualHash, pdf);
            repository.complete(entry.documentKey(), count);
            return new Outcome(false, count);
        } catch (Exception ex) {
            repository.fail(entry.documentKey(), ex.getMessage());
            throw ex;
        }
    }

    private int indexPdf(NcertCorpusManifest.Entry entry, String corpusVersion, String hash, Path pdf) throws IOException {
        TokenTextSplitter splitter = new TokenTextSplitter(chunkSize, chunkOverlap, 10, 5000, true);
        List<Document> batch = new ArrayList<>(batchSize);
        int chunkIndex = 0;
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(document));
                if (text.isBlank()) continue;
                Document pageDocument = new Document(text, Map.of("pageNumber", page));
                for (Document chunk : splitter.apply(List.of(pageDocument))) {
                    Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
                    metadata.put("source", "NCERT");
                    metadata.put("sourceType", "TEXTBOOK");
                    metadata.put("corpusVersion", corpusVersion);
                    metadata.put("chunkVersion", chunkVersion);
                    metadata.put("language", upper(entry.language()));
                    metadata.put("classLevel", upper(entry.classLevel()));
                    metadata.put("subject", upper(entry.subject()));
                    metadata.put("targetLevels", entry.targetLevels() == null
                            ? List.of(upper(entry.classLevel()))
                            : entry.targetLevels().stream().map(this::upper).distinct().toList());
                    metadata.put("bookCode", entry.bookCode());
                    metadata.put("bookTitle", entry.bookTitle());
                    metadata.put("chapterNumber", entry.chapterNumber());
                    metadata.put("chapterTitle", entry.chapterTitle());
                    metadata.put("fileName", entry.fileName());
                    metadata.put("sourceUrl", entry.sourceUrl());
                    metadata.put("contentHash", hash);
                    metadata.put("chunkIndex", chunkIndex);
                    metadata.put("contentScope", blankToDefault(entry.contentScope(), "CHAPTER"));
                    batch.add(new Document(deterministicChunkId(entry.documentKey(), chunkVersion, chunkIndex), chunk.getText(), metadata));
                    chunkIndex++;
                    if (batch.size() >= batchSize) { vectorStore.add(batch); batch.clear(); }
                }
            }
        }
        if (!batch.isEmpty()) vectorStore.add(batch);
        log.info("Indexed {} NCERT chunks for {}", chunkIndex, entry.documentKey());
        return chunkIndex;
    }

    private void deleteExistingChunks(String documentKey, String version, int previousCount) {
        if (previousCount <= 0) return;
        List<String> ids = new ArrayList<>(previousCount);
        for (int i = 0; i < previousCount; i++) ids.add(deterministicChunkId(documentKey, version, i));
        for (int i = 0; i < ids.size(); i += batchSize) vectorStore.delete(ids.subList(i, Math.min(i + batchSize, ids.size())));
    }

    private Path safeResolve(String fileName) throws IOException {
        Path resolved = root.resolve(fileName).normalize();
        if (!resolved.startsWith(root)) throw new IOException("Manifest file escapes NCERT corpus root: " + fileName);
        if (!fileName.toLowerCase().endsWith(".pdf")) throw new IOException("NCERT corpus entry is not a PDF: " + fileName);
        return resolved;
    }

    private void validateEntry(NcertCorpusManifest.Entry e) {
        if (e == null || blank(e.documentKey()) || blank(e.fileName()) || blank(e.subject()) || blank(e.bookCode())
                || blank(e.language()) || blank(e.classLevel())) throw new IllegalArgumentException("Manifest entry is missing a required field");
        if (e.targetLevels() != null && e.targetLevels().stream().anyMatch(this::blank)) {
            throw new IllegalArgumentException("Manifest targetLevels contains a blank value: " + e.documentKey());
        }
    }

    private String deterministicChunkId(String key, String version, int index) {
        return "ncert-" + sha256Text(key + "|" + version + "|" + index).substring(0, 48);
    }
    private String sha256(Path file) throws IOException { return digest(Files.readAllBytes(file)); }
    private String sha256Text(String value) { return digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    private String normalize(String text) { return text.replace('\u0000', ' ').replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\\n\\n").trim(); }
    private String upper(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String blankToDefault(String value, String fallback) { return blank(value) ? fallback : value; }

    public record Outcome(boolean skipped, int chunks) {}
    public record RunSummary(int entries, int completed, int skipped, int failed, int chunks, String corpusVersion) {}
}
