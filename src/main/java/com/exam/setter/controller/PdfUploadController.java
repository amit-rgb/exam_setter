package com.exam.setter.controller;

import com.exam.setter.service.PdfIngestionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
public class PdfUploadController {

    private final PdfIngestionService pdfIngestionService;

    public PdfUploadController(PdfIngestionService pdfIngestionService) { this.pdfIngestionService = pdfIngestionService; }

    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subject") String subject,
            @RequestParam("targetLevel") String targetLevel,
            @RequestParam(value = "sourceType", defaultValue = "STUDY_NOTES") String sourceType,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "chapterTitle", required = false) String chapterTitle,
            @RequestParam(value = "topic", required = false) String topic) {
        try {
            List<String> targetLevels = Arrays.stream(targetLevel.split(","))
                    .map(String::trim).filter(v -> !v.isBlank()).map(String::toUpperCase).distinct().toList();
            Map<String,Object> metadata = new java.util.LinkedHashMap<>();
            if (language != null && !language.isBlank()) metadata.put("language", language.trim().toUpperCase());
            if (chapterTitle != null && !chapterTitle.isBlank()) metadata.put("chapterTitle", chapterTitle.trim());
            if (topic != null && !topic.isBlank()) metadata.put("topic", topic.trim());
            int chunks = pdfIngestionService.ingest(file, subject, targetLevels, sourceType, metadata);
            return ResponseEntity.ok(Map.of("status","SUCCESS","fileName",file.getOriginalFilename(),"subject",subject,
                    "targetLevels",targetLevels,"sourceType",sourceType.trim().toUpperCase(),"indexedChunks",chunks));
        } catch (IOException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status","FAILED","error",e.getMessage()));
        }
    }
}