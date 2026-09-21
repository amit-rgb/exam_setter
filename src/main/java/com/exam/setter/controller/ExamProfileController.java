package com.exam.setter.controller;

import com.exam.setter.dto.ExamProfileRequest;
import com.exam.setter.entity.ExamProfileEntity;
import com.exam.setter.service.ExamProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/exam-profiles")
public class ExamProfileController {

    private final ExamProfileService service;

    public ExamProfileController(ExamProfileService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExamProfileEntity> list() { return service.list(); }

    @GetMapping("/{examId}")
    public ExamProfileEntity get(@PathVariable String examId) { return service.get(examId); }

    @PostMapping
    public ResponseEntity<ExamProfileEntity> save(@Valid @RequestBody ExamProfileRequest request) {
        return ResponseEntity.ok(service.save(request));
    }

    @GetMapping("/{examId}/generation-constraints")
    public Map<String, Object> generationConstraints(@PathVariable String examId) {
        return service.toGenerationConstraints(service.get(examId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
