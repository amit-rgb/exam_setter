package com.exam.setter.ncert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ncert.ingestion.enabled", havingValue = "true")
public class NcertCorpusIngestionRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(NcertCorpusIngestionRunner.class);
    private final NcertCorpusIngestionService ingestionService;

    public NcertCorpusIngestionRunner(NcertCorpusIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        NcertCorpusIngestionService.RunSummary summary = ingestionService.ingestManifest();
        log.info("NCERT corpus run complete: entries={}, completed={}, skipped={}, failed={}, chunks={}, corpusVersion={}",
                summary.entries(), summary.completed(), summary.skipped(), summary.failed(), summary.chunks(), summary.corpusVersion());
        if (summary.failed() > 0) {
            throw new IllegalStateException("NCERT ingestion completed with " + summary.failed() + " failed entries");
        }
    }
}
