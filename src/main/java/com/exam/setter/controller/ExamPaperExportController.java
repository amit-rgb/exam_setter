package com.exam.setter.controller;

import com.exam.setter.dto.AssembledExamPaper;
import com.exam.setter.dto.ExamPaperBlueprintRequest;
import com.exam.setter.service.ExamPaperService;
import com.exam.setter.service.HtmlExamPaperRenderer;
import com.exam.setter.service.LatexExportService;
import com.exam.setter.service.OpenHtmlToPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/exam-papers/export")
public class ExamPaperExportController {

    private final ExamPaperService examPaperService;
    private final LatexExportService latexExportService;
    private final HtmlExamPaperRenderer htmlRenderer;
    private final OpenHtmlToPdfService pdfService;

    public ExamPaperExportController(ExamPaperService examPaperService,
                                     LatexExportService latexExportService,
                                     HtmlExamPaperRenderer htmlRenderer,
                                     OpenHtmlToPdfService pdfService) {
        this.examPaperService = examPaperService;
        this.latexExportService = latexExportService;
        this.htmlRenderer = htmlRenderer;
        this.pdfService = pdfService;
    }

    @PostMapping("/latex")
    public ResponseEntity<String> exportToLatex(
            @RequestBody ExamPaperBlueprintRequest request,
            @RequestParam(value = "includeSolutions", defaultValue = "false") boolean includeSolutions) {

        AssembledExamPaper paper = examPaperService.assembleExamPaper(request);
        String latexContent = latexExportService.generateLatexDocument(paper, includeSolutions);
        String fileName = String.format("%s_%s.tex",
                paper.subject().toLowerCase(),
                includeSolutions ? "solutions" : "question_paper");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(latexContent);
    }

    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportToPdf(
            @RequestBody ExamPaperBlueprintRequest request,
            @RequestParam(value = "includeSolutions", defaultValue = "false") boolean includeSolutions) throws IOException {

        // 1. Assemble complete exam paper via RAG
        AssembledExamPaper paper = examPaperService.assembleExamPaper(request);

        // 2. Render to CSS-Paged HTML
        String htmlContent = htmlRenderer.renderHtml(paper, includeSolutions);

        // 3. Convert HTML to PDF in-memory (No OS binary needed)
        byte[] pdfBytes = pdfService.generatePdfFromHtml(htmlContent);

        String fileName = String.format("%s_%s.pdf",
                paper.subject().toLowerCase(),
                includeSolutions ? "solutions" : "question_paper");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}