package com.exam.setter.ncert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ncert.ingestion.enabled", havingValue = "true")
public class NcertCorpusIngestionRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(NcertCorpusIngestionRunner.class);
    private final NcertCorpusIngestionService ingestionService;
    private final ConfigurableApplicationContext context;

    public NcertCorpusIngestionRunner(NcertCorpusIngestionService ingestionService, ConfigurableApplicationContext context) {
        this.ingestionService = ingestionService;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            NcertCorpusIngestionService.RunSummary summary = ingestionService.ingestManifest();
            log.info("NCERT corpus run complete: entries={}, completed={}, skipped={}, failed={}, chunks={}, corpusVersion={}",
                    summary.entries(), summary.completed(), summary.skipped(), summary.failed(), summary.chunks(), summary.corpusVersion());
            if (summary.failed() > 0) exitCode = 1;
        } catch (Exception ex) {
            log.error("NCERT corpus run failed", ex);
            exitCode = 1;
        }
        int finalExitCode = exitCode;
        System.exit(SpringApplication.exit(context, () -> finalExitCode));
    }
}
