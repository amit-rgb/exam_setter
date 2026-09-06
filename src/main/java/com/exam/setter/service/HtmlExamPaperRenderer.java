package com.exam.setter.service;

import com.exam.setter.entity.ExamPaperEntity;
import com.exam.setter.entity.ExamSectionEntity;
import com.exam.setter.entity.QuestionEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HtmlExamPaperRenderer {

    /**
     * Renders strict XHTML directly from JPA Entity for Flying Saucer OpenPDF.
     */
    public String renderHtmlFromEntity(ExamPaperEntity paper, boolean includeSolutions) {
        StringBuilder sb = new StringBuilder();

        int selectedTotalMarks = paper.getSections() == null ? 0 : paper.getSections().stream()
                .filter(section -> section.getQuestions() != null)
                .flatMap(section -> section.getQuestions().stream())
                .filter(QuestionEntity::isIncludedInPaper)
                .mapToInt(QuestionEntity::getMarks)
                .sum();

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        sb.append("<head>\n");
        sb.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
        sb.append("<style type=\"text/css\">\n");
        sb.append("  @page {\n");
        sb.append("    size: A4;\n");
        sb.append("    margin: 20mm 15mm 20mm 15mm;\n");
        sb.append("    @bottom-center { content: counter(page); font-size: 10pt; font-family: Helvetica, sans-serif; }\n");
        sb.append("  }\n");
        sb.append("  body { font-family: Helvetica, Arial, sans-serif; font-size: 11pt; line-height: 1.4; color: #111111; }\n");
        sb.append("  .header-box { text-align: center; border-bottom: 2px solid #000000; padding-bottom: 8px; margin-bottom: 12px; }\n");
        sb.append("  .header-box h1 { font-size: 18pt; margin: 0 0 6px 0; text-transform: uppercase; letter-spacing: 0.5px; }\n");
        sb.append("  .meta-bar { font-weight: bold; font-size: 10.5pt; margin-bottom: 4px; }\n");
        sb.append("  .instructions { font-size: 9.5pt; margin-bottom: 15px; padding: 6px; background-color: #f7f7f7; border-left: 3px solid #333333; }\n");
        sb.append("  .instructions ul { margin: 2px 0 2px 18px; padding: 0; }\n");
        sb.append("  .section-title { font-size: 12pt; font-weight: bold; background: #eeeeee; padding: 4px 8px; margin-top: 16px; margin-bottom: 8px; border-left: 4px solid #000000; }\n");
        sb.append("  .neg-mark { font-size: 8.5pt; font-style: italic; color: #555555; margin-left: 8px; font-weight: normal; }\n");
        sb.append("  .question-container { margin-bottom: 14px; page-break-inside: avoid; }\n");
        sb.append("  .q-text { font-weight: bold; margin-bottom: 4px; }\n");
        sb.append("  .options-table { width: 100%; margin-left: 15px; margin-top: 4px; border-collapse: collapse; }\n");
        sb.append("  .options-table td { padding: 3px 0; vertical-align: top; }\n");
        sb.append("  .solution-box { margin-top: 6px; margin-left: 15px; padding: 6px 8px; background: #fdf6e2; border-left: 3px solid #b58900; font-size: 9.5pt; }\n");
        sb.append("</style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");

        // Header Box
        sb.append("<div class=\"header-box\">\n");
        sb.append("  <h1>").append(escapeXml(paper.getTitle())).append("</h1>\n");
        sb.append("  <div class=\"meta-bar\">\n");
        sb.append("    <span>SUBJECT: ").append(escapeXml(paper.getSubject().toUpperCase())).append("</span> | ");
        sb.append("    <span>DURATION: ").append(paper.getDurationMinutes()).append(" MIN</span> | ");
        sb.append("    <span>MAX MARKS: ").append(selectedTotalMarks).append("</span>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        // General Instructions
        sb.append("<div class=\"instructions\">\n");
        sb.append("  <strong>General Instructions:</strong>\n");
        sb.append("  <ul>\n");
        sb.append("    <li>All questions are compulsory.</li>\n");
        sb.append("    <li>Calculators or digital log tables are strictly prohibited.</li>\n");
        sb.append("    <li>Standard SI units must be observed in all numerical solutions.</li>\n");
        sb.append("  </ul>\n");
        sb.append("</div>\n");

        // Sections and Questions
        int qCounter = 1;
        if (paper.getSections() != null) {
            for (ExamSectionEntity section : paper.getSections()) {
                List<QuestionEntity> questions = section.getQuestions();
                if (questions == null || questions.isEmpty()) {
                    continue;
                }

                long selectedQuestionCount = questions.stream()
                        .filter(QuestionEntity::isIncludedInPaper)
                        .count();
                if (selectedQuestionCount == 0) {
                    continue;
                }
                int selectedSectionMarks = questions.stream()
                        .filter(QuestionEntity::isIncludedInPaper)
                        .mapToInt(QuestionEntity::getMarks)
                        .sum();

                sb.append("<div class=\"section-title\">\n");
                sb.append("  <span>").append(escapeXml(section.getSectionName())).append("</span>\n");
                sb.append("  <span style=\"float: right;\">[Section Marks: ").append(selectedSectionMarks).append("]</span>\n");
                if (section.getNegativeMarks() > 0) {
                    sb.append("  <span class=\"neg-mark\">(Negative Marking: -").append(section.getNegativeMarks()).append(")</span>\n");
                }
                sb.append("</div>\n");

                for (QuestionEntity q : questions) {
                    if (!q.isIncludedInPaper()) continue;
                    sb.append("<div class=\"question-container\">\n");
                    sb.append("  <div class=\"q-text\">Q").append(qCounter++).append(". [")
                            .append(q.getMarks()).append(" Mark(s)] ")
                            .append(escapeXml(cleanMath(q.getQuestionText())))
                            .append("</div>\n");

                    // Render MCQ Options using strict XML tables
                    if (q.getOptions() != null && !q.getOptions().isEmpty()) {
                        sb.append("  <table class=\"options-table\">\n");
                        char label = 'A';
                        for (String opt : q.getOptions()) {
                            sb.append("    <tr>\n");
                            sb.append("      <td style=\"width: 30px;\"><strong>(").append(label++).append(")</strong></td>\n");
                            sb.append("      <td>").append(escapeXml(cleanMath(opt))).append("</td>\n");
                            sb.append("    </tr>\n");
                        }
                        sb.append("  </table>\n");
                    }

                    if (includeSolutions) {
                        sb.append("  <div class=\"solution-box\">\n");
                        sb.append("    <div><strong>Correct Answer:</strong> ").append(escapeXml(cleanMath(q.getCorrectAnswer()))).append("</div>\n");
                        sb.append("    <div style=\"margin-top: 3px;\"><strong>Explanation:</strong> ").append(escapeXml(cleanMath(q.getExplanation()))).append("</div>\n");
                        sb.append("  </div>\n");
                    }

                    sb.append("</div>\n");
                }
            }
        }

        sb.append("</body>\n");
        sb.append("</html>");
        return sb.toString();
    }

    private String cleanMath(String text) {
        if (text == null) return "";

        // Normalize malformed isotope notation like ^{80_{35Br or ^{80}_{35}Br to 80/35 Br
        String cleaned = text.replaceAll("\\^\\{?(\\d+)_\\{?(\\d+)\\s*([A-Za-z]+)\\}?", "[$1/$2 $3]")
                .replaceAll("\\^\\{?(\\d+)\\}?_\\{?(\\d+)\\}?\\s*([A-Za-z]+)", "[$1/$2 $3]");

        // Normalize inline / display LaTeX delimiters and standard commands
        cleaned = cleaned.replace("$$", "")
                .replace("$", "")
                .replace("\\(", "")
                .replace("\\)", "")
                .replace("\\[", "")
                .replace("\\]", "")
                .replace("\\rightarrow", " -> ")
                .replace("ightarrow", " -> ")
                .replace("\\times", " x ")
                .replace("\\Delta", "Delta")
                .replace("\\text{", "")
                .replace("\\mathrm{", "")
                .replace("}", "");

        return cleaned;
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}