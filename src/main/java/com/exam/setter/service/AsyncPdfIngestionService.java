package com.exam.setter.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AsyncPdfIngestionService {

    private final PdfIngestionService ingestionService;

    public AsyncPdfIngestionService(PdfIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Async
    public CompletableFuture<Integer> ingest(byte[] bytes, String fileName, String contentType,
                                             String subject, List<String> targetLevels,
                                             String sourceType, java.util.Map<String,Object> metadata) {
        try {
            MultipartFile file = new ByteArrayMultipartFile(bytes, fileName, contentType);
            return CompletableFuture.completedFuture(
                    ingestionService.ingest(file, subject, targetLevels, sourceType, metadata));
        } catch (Exception ex) {
            CompletableFuture<Integer> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    private static final class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] bytes;
        private final String name;
        private final String contentType;

        private ByteArrayMultipartFile(byte[] bytes, String name, String contentType) {
            this.bytes = bytes;
            this.name = name;
            this.contentType = contentType;
        }

        public String getName() { return "file"; }
        public String getOriginalFilename() { return name; }
        public String getContentType() { return contentType; }
        public boolean isEmpty() { return bytes.length == 0; }
        public long getSize() { return bytes.length; }
        public byte[] getBytes() { return bytes; }
        public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        public void transferTo(java.io.File dest) throws IOException { java.nio.file.Files.write(dest.toPath(), bytes); }
    }
}
