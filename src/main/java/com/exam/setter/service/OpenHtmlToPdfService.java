package com.exam.setter.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class OpenHtmlToPdfService {

    public byte[] generatePdfFromHtml(String htmlContent) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            try {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(htmlContent, null);
                builder.toStream(outputStream);
                builder.run();
                return outputStream.toByteArray();
            } catch (Exception e) {
                throw new IOException("Failed to render PDF using OpenHTMLtoPDF: " + e.getMessage(), e);
            }
        }
    }
}
