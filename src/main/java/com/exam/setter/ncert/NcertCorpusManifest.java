package com.exam.setter.ncert;

import java.util.List;

/** Immutable manifest contract produced by the NCERT downloader. */
public record NcertCorpusManifest(
        String schemaVersion,
        String corpusVersion,
        List<Entry> entries) {

    public record Entry(
            String documentKey,
            String language,
            String classLevel,
            String subject,
            String bookCode,
            String bookTitle,
            Integer chapterNumber,
            String chapterTitle,
            String fileName,
            String sourceUrl,
            List<String> targetLevels,
            String contentScope,
            String contentHash) {
    }
}
