package com.exam.setter.controller;

import com.exam.setter.service.ExamPatternAnalyzerService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/exam-patterns")
public class ExamPatternController {

    private final ExamPatternAnalyzerService analyzerService;

    public ExamPatternController(ExamPatternAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    @GetMapping("/{examId}/analyze")
    public Map<String, Object> analyze(@PathVariable String examId) {
        return analyzerService.analyze(examId);
    }
}
