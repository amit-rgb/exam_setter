package com.exam.setter.controller;

import com.exam.setter.service.AsyncPdfIngestionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
public class AsyncPdfIngestionController {

    private final AsyncPdfIngestionService service;

    public AsyncPdfIngestionController(AsyncPdfIngestionService service) {
        this.service = service;
    }

    @PostMapping(value = "/pdf/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String,Object>> ingestAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subject") String subject,
            @RequestParam("targetLevel") String targetLevel,
            @RequestParam(value = "sourceType", defaultValue = "STUDY_NOTES") String sourceType,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "chapterTitle", required = false) String chapterTitle,
            @RequestParam(value = "topic", required = false) String topic) throws Exception {

        List<String> levels = Arrays.stream(targetLevel.split(","))
                .map(String::trim).filter(v -> !v.isBlank()).map(String::toUpperCase).distinct().toList();
        Map<String,Object> metadata = new LinkedHashMap<>();
        if (language != null && !language.isBlank()) metadata.put("language", language.trim().toUpperCase());
        if (chapterTitle != null && !chapterTitle.isBlank()) metadata.put("chapterTitle", chapterTitle.trim());
        if (topic != null && !topic.isBlank()) metadata.put("topic", topic.trim());

        byte[] bytes = file.getBytes();
        service.ingest(bytes, file.getOriginalFilename(), file.getContentType(),
                subject, levels, sourceType, metadata);

        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "message", "Document ingestion started in the background.",
                "fileName", file.getOriginalFilename(),
                "targetLevels", levels,
                "sourceType", sourceType.toUpperCase(),
                "refresh", "Use the ingestion dashboard to monitor pipeline status."));
    }
}
