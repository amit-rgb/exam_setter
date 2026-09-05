package com.exam.setter.service;

import com.exam.setter.dto.AssembledExamPaper;
import com.exam.setter.dto.GeneratedQuestion;
import org.springframework.stereotype.Service;

@Service
public class HtmlExamPaperRenderer {

    public String renderHtml(AssembledExamPaper paper, boolean includeSolutions) {
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\"/>\n");
        sb.append("<style>\n");
        sb.append("@page {\n");
        sb.append("  size: A4;\n");
        sb.append("  margin: 20mm 15mm 20mm 15mm;\n");
        sb.append("  @bottom-center { content: counter(page); font-size: 10pt; font-family: 'Helvetica', sans-serif; }\n");
        sb.append("}\n");
        sb.append("body { font-family: 'Helvetica', 'Arial', sans-serif; font-size: 11pt; line-height: 1.4; color: #111; }\n");
        sb.append(".header-box { text-align: center; border-bottom: 2px solid #000; padding-bottom: 8px; margin-bottom: 12px; }\n");
        sb.append(".header-box h1 { font-size: 18pt; margin: 0 0 6px 0; text-transform: uppercase; letter-spacing: 0.5px; }\n");
        sb.append(".meta-bar { font-weight: bold; font-size: 10.5pt; display: flex; justify-content: space-between; }\n");
        sb.append(".instructions { font-size: 9.5pt; margin-bottom: 15px; padding: 6px; background-color: #f7f7f7; border-left: 3px solid #333; }\n");
        sb.append(".instructions ul { margin: 2px 0 2px 18px; padding: 0; }\n");
        sb.append(".section-title { font-size: 12.5pt; font-weight: bold; background: #eee; padding: 4px 8px; margin-top: 18px; border-left: 4px solid #000; }\n");
        sb.append(".neg-mark { font-size: 8.5pt; font-style: italic; color: #555; margin-left: 8px; }\n");
        sb.append(".question-container { margin-bottom: 12px; page-break-inside: avoid; }\n");
        sb.append(".q-text { font-weight: bold; margin-bottom: 4px; }\n");
        sb.append(".options-grid { margin-left: 20px; }\n");
        sb.append(".option-item { margin-bottom: 2px; }\n");
        sb.append(".solution-box { margin-top: 5px; margin-left: 20px; padding: 6px; background: #fdf6e2; border-left: 3px solid #b58900; font-size: 9.5pt; }\n");
        sb.append("</style>\n</head>\n<body>\n");

        // Header Section
        sb.append("<div class=\"header-box\">\n");
        sb.append("<h1>").append(escapeHtml(paper.examTitle())).append("</h1>\n");
        sb.append("<div class=\"meta-bar\">\n");
        sb.append("<span>SUBJECT: ").append(escapeHtml(paper.subject().toUpperCase())).append("</span> | ");
        sb.append("<span>DURATION: ").append(paper.durationMinutes()).append(" MIN</span> | ");
        sb.append("<span>MAX MARKS: ").append(paper.totalMarks()).append("</span>\n");
        sb.append("</div>\n</div>\n");

        // General Instructions
        sb.append("<div class=\"instructions\">\n<strong>General Instructions:</strong>\n<ul>\n");
        sb.append("<li>All questions are compulsory.</li>\n");
        sb.append("<li>Calculators or digital log tables are strictly prohibited.</li>\n");
        sb.append("<li>Standard SI units must be observed in all numerical solutions.</li>\n");
        sb.append("</ul>\n</div>\n");

        // Sections and Questions
        int qCounter = 1;
        for (AssembledExamPaper.AssembledSection section : paper.sections()) {
            sb.append("<div class=\"section-title\">")
                    .append(escapeHtml(section.sectionName()))
                    .append(" <span style=\"float:right;\">[").append(section.totalSectionMarks()).append(" Marks]</span>");

            if (section.negativeMarksPerQuestion() > 0) {
                sb.append("<span class=\"neg-mark\">(Negative Marking: -").append(section.negativeMarksPerQuestion()).append(")</span>");
            }
            sb.append("</div>\n");

            for (GeneratedQuestion q : section.questions()) {
                sb.append("<div class=\"question-container\">\n");
                sb.append("<div class=\"q-text\">Q").append(qCounter++).append(". [")
                        .append(q.marks()).append(" Mark(s)] ")
                        .append(cleanFormulaText(q.questionText()))
                        .append("</div>\n");

                if (q.options() != null && !q.options().isEmpty()) {
                    sb.append("<div class=\"options-grid\">\n");
                    char label = 'A';
                    for (String opt : q.options()) {
                        sb.append("<div class=\"option-item\"><strong>(")
                                .append(label++)
                                .append(")</strong> ")
                                .append(cleanFormulaText(opt))
                                .append("</div>\n");
                    }
                    sb.append("</div>\n");
                }

                if (includeSolutions) {
                    sb.append("<div class=\"solution-box\">\n");
                    sb.append("<div><strong>Answer:</strong> ").append(cleanFormulaText(q.correctAnswer())).append("</div>\n");
                    sb.append("<div><strong>Solution:</strong> ").append(cleanFormulaText(q.explanation())).append("</div>\n");
                    sb.append("</div>\n");
                }

                sb.append("</div>\n");
            }
        }

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private String cleanFormulaText(String text) {
        if (text == null) return "";
        // Clean math delimiters and arrows for standard browser/PDF typography
        return text.replace("$$", "")
                .replace("$", "")
                .replace("\\rightarrow", " → ")
                .replace("ightarrow", " → ")
                .replace("\\times", " × ")
                .replace("\\Delta", "Δ")
                .replace("\\text{", "")
                .replace("}", "");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}