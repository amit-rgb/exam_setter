package com.exam.setter.controller;

import com.exam.setter.service.PdfIngestionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
public class PdfUploadController {

    private final PdfIngestionService pdfIngestionService;

    public PdfUploadController(PdfIngestionService pdfIngestionService) {
        this.pdfIngestionService = pdfIngestionService;
    }

    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadBookPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subject") String subject,
            @RequestParam("targetLevel") String targetLevel, // e.g. "CLASS_11", "CLASS_12", "NEET"
            @RequestParam(value = "sourceType", defaultValue = "TEXTBOOK") String sourceType) {

        try {
            int chunksCreated = pdfIngestionService.ingestPdfFile(file, subject, targetLevel, sourceType);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "fileName", file.getOriginalFilename(),
                    "subject", subject,
                    "targetLevel", targetLevel,
                    "indexedChunks", chunksCreated
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            ));
        }
    }
}