package com.exam.setter.controller;

import com.exam.setter.entity.PyqQuestionEntity;
import com.exam.setter.service.PyqIngestionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest/pyq")
public class PyqIngestionController {

    private final PyqIngestionService service;

    public PyqIngestionController(PyqIngestionService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestParam("file") MultipartFile file,
            @RequestParam("examId") String examId,
            @RequestParam("year") int year,
            @RequestParam(value = "paperName", required = false) String paperName,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "targetLevel", required = false) String targetLevel) throws IOException {
        List<PyqQuestionEntity> questions = service.ingestPdf(file, examId, year, paperName, subject, targetLevel);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "examId", examId.trim().toUpperCase(),
                "year", year,
                "sourceFileName", file.getOriginalFilename(),
                "questionsExtracted", questions.size(),
                "targetLevel", targetLevel == null ? "" : targetLevel
        ));
    }
}
