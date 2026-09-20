package com.exam.setter.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PdfIngestionServiceTest {

    @Test
    void rejectsPreviousYearPapersOnGenericUploadPath() {
        PdfIngestionService service = new PdfIngestionService(mock(VectorStore.class));
        MockMultipartFile file = new MockMultipartFile("file", "paper.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.ingestPdfFile(file, "chemistry", "CLASS_11", "PREVIOUS_YEAR_PAPER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dedicated PYQ workflow");
    }

    @Test
    void parsesMultipleTargetLevelsForGenericUpload() {
        PdfIngestionService service = new PdfIngestionService(mock(VectorStore.class));
        org.assertj.core.api.Assertions.assertThat(service.parseTargetLevelsForTest("class_11, CLASS_12, class_11"))
                .containsExactly("CLASS_11", "CLASS_12");
    }
}
