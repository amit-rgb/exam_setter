package com.exam.setter.controller;

import com.exam.setter.dto.GeneratedQuestion;
import com.exam.setter.dto.QuestionGenerationRequest;
import com.exam.setter.service.QuestionGeneratorService;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionGeneratorService questionGeneratorService;
    private final ExamAwareQuestionGenerationService examAwareQuestionGenerationService;

    public QuestionController(QuestionGeneratorService questionGeneratorService, ExamAwareQuestionGenerationService examAwareQuestionGenerationService) {
        this.questionGeneratorService = questionGeneratorService;
        this.examAwareQuestionGenerationService = examAwareQuestionGenerationService;
    }

    @PostMapping("/generate")
    public ResponseEntity<List<GeneratedQuestion>> generateQuestions(
            @Valid @RequestBody QuestionGenerationRequest request) {
        List<GeneratedQuestion> questions = questionGeneratorService.generateQuestions(request);
        return ResponseEntity.ok(questions);
    }
}