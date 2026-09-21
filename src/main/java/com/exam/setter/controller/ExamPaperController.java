package com.exam.setter.controller;

import com.exam.setter.dto.ExamPaperBlueprintRequest;
import com.exam.setter.entity.ExamPaperEntity;
import com.exam.setter.repository.ExamPaperRepository;
import com.exam.setter.service.ExamPaperService;
import com.exam.setter.service.HtmlExamPaperRenderer;
import com.exam.setter.service.OpenHtmlToPdfService;
import com.exam.setter.service.GenerationDiagnosticService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/exam-papers")
public class ExamPaperController {

    private final ExamPaperService examPaperService;
    private final ExamPaperRepository paperRepository;
    private final HtmlExamPaperRenderer htmlRenderer;
    private final OpenHtmlToPdfService pdfService;
    private final GenerationDiagnosticService diagnosticService;

    public ExamPaperController(ExamPaperService examPaperService,
                               ExamPaperRepository paperRepository,
                               HtmlExamPaperRenderer htmlRenderer,
                               OpenHtmlToPdfService pdfService,
                               GenerationDiagnosticService diagnosticService) {
        this.examPaperService = examPaperService;
        this.paperRepository = paperRepository;
        this.htmlRenderer = htmlRenderer;
        this.pdfService = pdfService;
        this.diagnosticService = diagnosticService;
    }

    @PostMapping("/assemble")
    public ResponseEntity<?> assemblePaper(@Valid @RequestBody ExamPaperBlueprintRequest request) {
        try {
            ExamPaperEntity savedPaper = examPaperService.assembleAndPersistExamPaper(request);
            return ResponseEntity.ok(savedPaper);
        } catch (Exception ex) {
            diagnosticService.recordFailure(java.util.Map.of(
                    "operation", "assemblePaper",
                    "subject", request.subject() == null ? "" : request.subject(),
                    "targetLevels", request.targetLevels() == null ? java.util.List.of() : request.targetLevels(),
                    "selectedSources", request.knowledgeSources() == null ? java.util.List.of() : request.knowledgeSources(),
                    "examId", request.examId() == null ? "" : request.examId()
            ), ex);
            org.slf4j.LoggerFactory.getLogger(ExamPaperController.class)
                    .error("Exam paper generation failed: subject={}, levels={}, sources={}",
                            request.subject(), request.targetLevels(), request.knowledgeSources(), ex);
            return ResponseEntity.internalServerError().body(java.util.Map.of(
                    "status", "FAILED",
                    "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                    "diagnostics", "/diagnostics.html"
            ));
        }
    }

    @PutMapping("/{paperId}/selection")
    public ResponseEntity<ExamPaperEntity> updateQuestionSelection(
            @PathVariable UUID paperId,
            @RequestBody com.exam.setter.dto.QuestionSelectionRequest request) {
        return ResponseEntity.ok(examPaperService.updateQuestionSelection(paperId, request.includedQuestionIds()));
    }

    @GetMapping(value = "/{paperId}/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPaperToPdf(
            @PathVariable UUID paperId,
            @RequestParam(value = "includeSolutions", defaultValue = "false") boolean includeSolutions) throws IOException {

        ExamPaperEntity paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new RuntimeException("Paper not found with ID: " + paperId));

        String htmlContent = htmlRenderer.renderHtmlFromEntity(paper, includeSolutions);
        byte[] pdfBytes = pdfService.generatePdfFromHtml(htmlContent);

        String fileName = String.format("%s_%s.pdf",
                paper.getSubject().toLowerCase(),
                includeSolutions ? "solutions" : "question_paper");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}