package com.exam.setter.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class PdfExportService {

    public byte[] compileLatexToPdf(String latexContent) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("latex_build_");
        Path texFile = tempDir.resolve("paper.tex");
        Path pdfFile = tempDir.resolve("paper.pdf");

        // Write .tex file to disk
        Files.writeString(texFile, latexContent);

        // Execute pdflatex process
        ProcessBuilder pb = new ProcessBuilder(
                "pdflatex",
                "-interaction=nonstopmode",
                "-output-directory=" + tempDir.toAbsolutePath(),
                texFile.toAbsolutePath().toString()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);

        if (!finished || !Files.exists(pdfFile)) {
            process.destroyForcibly();
            cleanupDirectory(tempDir.toFile());
            throw new RuntimeException("LaTeX PDF compilation failed or pdflatex is not installed on host machine.");
        }

        byte[] pdfBytes = Files.readAllBytes(pdfFile);
        cleanupDirectory(tempDir.toFile());
        return pdfBytes;
    }

    private void cleanupDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        dir.delete();
    }
}