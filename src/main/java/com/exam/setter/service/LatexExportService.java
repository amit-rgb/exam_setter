package com.exam.setter.service;

import com.exam.setter.dto.AssembledExamPaper;
import com.exam.setter.dto.GeneratedQuestion;
import org.springframework.stereotype.Service;

@Service
public class LatexExportService {

    public String generateLatexDocument(AssembledExamPaper paper, boolean includeSolutions) {
        StringBuilder sb = new StringBuilder();

        sb.append("\\documentclass[12pt,a4paper]{article}\n");
        sb.append("\\usepackage[utf8]{inputenc}\n");
        sb.append("\\usepackage{amsmath,amssymb,amsfonts}\n");
        sb.append("\\usepackage{geometry}\n");
        sb.append("\\geometry{margin=1in}\n");
        sb.append("\\usepackage{enumitem}\n");
        sb.append("\\usepackage{fancyhdr}\n");
        sb.append("\\pagestyle{fancy}\n");
        sb.append("\\fancyhf{}\n");
        sb.append(String.format("\\lhead{%s}\n", escapeText(paper.subject().toUpperCase())));
        sb.append(String.format("\\rhead{%s}\n", includeSolutions ? "TEACHER COPY (SOLUTIONS)" : "STUDENT COPY"));
        sb.append("\\cfoot{\\thepage}\n\n");

        sb.append("\\begin{document}\n\n");

        // Header Box
        sb.append("\\begin{center}\n");
        sb.append(String.format("{\\LARGE \\textbf{%s}}\\\\[4pt]\n", escapeText(paper.examTitle())));
        sb.append(String.format("{\\textbf{Duration:} %d Minutes \\quad | \\quad \\textbf{Max Marks:} %d Marks}\\\\[6pt]\n",
                paper.durationMinutes(), paper.totalMarks()));
        sb.append("\\rule{\\textwidth}{1pt}\n");
        sb.append("\\end{center}\n\n");

        // General Instructions
        sb.append("\\noindent \\textbf{General Instructions:}\n");
        sb.append("\\begin{itemize}[noitemsep,topsep=0pt]\n");
        sb.append("  \\item All questions are compulsory unless specified otherwise.\n");
        sb.append("  \\item Use of calculators or mathematical log tables is strictly prohibited.\n");
        sb.append("  \\item Ensure all chemical formulas and calculations follow standard SI units.\n");
        sb.append("\\end{itemize}\n");
        sb.append("\\vspace{0.3cm}\n\n");

        // Sections
        for (AssembledExamPaper.AssembledSection section : paper.sections()) {
            sb.append(String.format("\\section*{%s \\hfill [Section Marks: %d]}\n",
                    escapeText(section.sectionName()), section.totalSectionMarks()));

            if (section.negativeMarksPerQuestion() > 0) {
                sb.append(String.format("\\noindent {\\small \\textit{Negative Marking: -%.2f marks for incorrect response.}}\\\\[6pt]\n",
                        section.negativeMarksPerQuestion()));
            }

            sb.append("\\begin{enumerate}[leftmargin=*]\n");
            for (GeneratedQuestion q : section.questions()) {
                sb.append(String.format("  \\item \\textbf{[%d Marks]} %s\n\n", q.marks(), cleanLatexMath(q.questionText())));

                if (q.options() != null && !q.options().isEmpty()) {
                    sb.append("  \\begin{enumerate}[label=(\\Alph*),itemsep=2pt]\n");
                    for (String opt : q.options()) {
                        sb.append(String.format("    \\item %s\n", cleanLatexMath(opt)));
                    }
                    sb.append("  \\end{enumerate}\n");
                }

                if (includeSolutions) {
                    sb.append("  \\vspace{0.2cm}\n");
                    sb.append("  \\noindent {\\bfseries Correct Answer:} \\textbf{");
                    sb.append(cleanLatexMath(q.correctAnswer())).append("}\\\\\n");
                    sb.append("  \\noindent {\\bfseries Solution/Explanation:} ");
                    sb.append(cleanLatexMath(q.explanation())).append("\n");
                    sb.append("  \\vspace{0.3cm}\n");
                } else {
                    sb.append("  \\vspace{0.4cm}\n");
                }
            }
            sb.append("\\end{enumerate}\n\n");
        }

        sb.append("\\end{document}\n");
        return sb.toString();
    }

    // Protects math equations while escaping stray characters
    private String cleanLatexMath(String input) {
        if (input == null) return "";
        // Clean accidental unescaped backslashes or corrupted arrows
        return input.replace("\\\\", "\\")
                .replace("\r", "")
                .replace("ightarrow", "\\rightarrow");
    }

    private String escapeText(String input) {
        if (input == null) return "";
        return input.replace("&", "\\&")
                .replace("%", "\\%")
                .replace("#", "\\#");
    }
}