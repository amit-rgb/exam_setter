package com.exam.setter.controller;

import com.exam.setter.dto.ReviewActionRequest;
import com.exam.setter.entity.ExamPaperEntity;
import com.exam.setter.entity.QuestionEntity;
import com.exam.setter.service.ExamModerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    private final ExamModerationService moderationService;

    public ModerationController(ExamModerationService moderationService) {
        this.moderationService = moderationService;
    }

    // 1-Click Review (Approve, Edit or Reject)
    @PostMapping("/review")
    public ResponseEntity<QuestionEntity> reviewQuestion(@RequestBody ReviewActionRequest request) {
        return ResponseEntity.ok(moderationService.reviewQuestion(request));
    }

    // Finalize Paper after reviewing
    @PostMapping("/papers/{paperId}/finalize")
    public ResponseEntity<ExamPaperEntity> finalizePaper(@PathVariable UUID paperId) {
        return ResponseEntity.ok(moderationService.checkAndFinalizePaper(paperId));
    }
}