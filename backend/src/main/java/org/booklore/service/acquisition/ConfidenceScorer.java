package org.booklore.service.acquisition;

import org.booklore.model.dto.acquisition.NzbResult;
import org.booklore.model.entity.WantedBookEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class ConfidenceScorer {

    private static final int THRESHOLD = 75;

    public int calculateConfidence(WantedBookEntity wanted, NzbResult result) {
        int score = 0;
        String nzbNorm = normalizeTitle(result.title());
        String wantedTitleNorm = normalizeTitle(wanted.getTitle());

        double coverage = titleCoverage(wantedTitleNorm, nzbNorm);
        if (coverage > 0.7) score += 60;
        else if (coverage > 0.5) score += 30;
        else if (coverage > 0.3) score += 15;

        if (wanted.getAuthor() != null) {
            String authorNorm = normalizeTitle(wanted.getAuthor());
            if (!authorNorm.isEmpty() && nzbNorm.contains(authorNorm)) {
                score += 20;
            }
        }

        if (wanted.getIsbn13() != null && result.title().contains(wanted.getIsbn13())) {
            score += 25;
        }

        if (wanted.getIsbn10() != null && result.title().contains(wanted.getIsbn10())) {
            score += 15;
        }

        if (nzbNorm.matches(".*(epub|mobi|pdf|azw3).*")) {
            score += 5;
        }

        if (nzbNorm.contains("audiobook") || nzbNorm.contains("audio book")) {
            score -= 30;
        }
        if (nzbNorm.contains("abridged")) {
            score -= 20;
        }

        return Math.min(100, Math.max(0, score));
    }

    public boolean meetsThreshold(int score) {
        return score >= THRESHOLD;
    }

    private double titleCoverage(String wantedTitle, String nzbTitle) {
        if (wantedTitle.isEmpty()) return 0.0;
        Set<String> wantedWords = new HashSet<>(Arrays.asList(wantedTitle.split("\\s+")));
        wantedWords.remove("");
        if (wantedWords.isEmpty()) return 0.0;
        Set<String> nzbWords = new HashSet<>(Arrays.asList(nzbTitle.split("\\s+")));
        long matched = wantedWords.stream().filter(nzbWords::contains).count();
        return (double) matched / wantedWords.size();
    }

    private String normalizeTitle(String input) {
        if (input == null) return "";
        return input.toLowerCase()
                .replaceAll("\\b(the|a|an)\\b", "")
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
