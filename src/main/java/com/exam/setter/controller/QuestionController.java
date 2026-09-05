package com.exam.setter.controller;

import com.exam.setter.dto.GeneratedQuestion;
import com.exam.setter.dto.QuestionGenerationRequest;
import com.exam.setter.service.QuestionGeneratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionGeneratorService questionGeneratorService;

    public QuestionController(QuestionGeneratorService questionGeneratorService) {
        this.questionGeneratorService = questionGeneratorService;
    }

    @PostMapping("/generate")
    public ResponseEntity<List<GeneratedQuestion>> generateQuestions(
            @RequestBody QuestionGenerationRequest request) {
        List<GeneratedQuestion> questions = questionGeneratorService.generateQuestions(request);
        return ResponseEntity.ok(questions);
    }
}