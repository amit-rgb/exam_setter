package com.exam.setter.controller;

import com.exam.setter.dto.AssembledExamPaper;
import com.exam.setter.dto.ExamPaperBlueprintRequest;
import com.exam.setter.service.ExamPaperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exam-papers")
public class ExamPaperController {

    private final ExamPaperService examPaperService;

    public ExamPaperController(ExamPaperService examPaperService) {
        this.examPaperService = examPaperService;
    }

    @PostMapping("/assemble")
    public ResponseEntity<AssembledExamPaper> assemblePaper(@RequestBody ExamPaperBlueprintRequest request) {
        AssembledExamPaper paper = examPaperService.assembleExamPaper(request);
        return ResponseEntity.ok(paper);
    }
}