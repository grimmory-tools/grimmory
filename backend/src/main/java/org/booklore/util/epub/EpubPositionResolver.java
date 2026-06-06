package org.booklore.util.epub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.util.koreader.EpubCfiService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpubPositionResolver {

    private static final Pattern XPOINTER_FRAGMENT_PATTERN =
            Pattern.compile("^/body/DocFragment\\[(\\d+)\\]");

    private static final Pattern XPOINTER_TEXT_OFFSET_PATTERN =
            Pattern.compile("/text\\(\\)\\.(\\d+)$|\\.\\s*(\\d+)$");

    private final EpubCfiService epubCfiService;

    public record ResolvedPosition(String href, Float chapterProgression) {}

    public Optional<ResolvedPosition> resolveFromCfi(Path epubPath, String cfi) {
        if (cfi == null || cfi.isBlank()) return Optional.empty();
        try {
            return epubCfiService.resolveCfiLocation(epubPath, cfi)
                    .map(loc -> new ResolvedPosition(
                            loc.href(),
                            loc.contentSourceProgressPercent() != null
                                    ? loc.contentSourceProgressPercent() / 100f
                                    : null));
        } catch (Exception e) {
            log.debug("CFI resolution failed for {}: {}", epubPath.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ResolvedPosition> resolveFromXPointer(Path epubPath, String xpointer) {
        if (xpointer == null || xpointer.isBlank()) return Optional.empty();
        try {
            Matcher fragmentMatcher = XPOINTER_FRAGMENT_PATTERN.matcher(xpointer);
            if (!fragmentMatcher.find()) {
                log.debug("XPointer has no DocFragment index, cannot resolve: {}", xpointer);
                return Optional.empty();
            }

            int spineIndex = Integer.parseInt(fragmentMatcher.group(1)) - 1;
            File epubFile = epubPath.toFile();
            String href = EpubContentReader.getSpineItemHref(epubFile, spineIndex);
            if (href == null || href.isBlank()) return Optional.empty();

            Matcher offsetMatcher = XPOINTER_TEXT_OFFSET_PATTERN.matcher(xpointer);
            if (!offsetMatcher.find()) {
                return Optional.of(new ResolvedPosition(href, null));
            }

            String rawOffset = offsetMatcher.group(1) != null ? offsetMatcher.group(1) : offsetMatcher.group(2);
            int charOffset = Integer.parseInt(rawOffset);

            String plainText = extractPlainText(epubFile, spineIndex);
            if (plainText.isEmpty()) {
                return Optional.of(new ResolvedPosition(href, null));
            }

            float progression = clamp((float) charOffset / (float) plainText.length());
            return Optional.of(new ResolvedPosition(href, progression));

        } catch (Exception e) {
            log.debug("XPointer resolution failed for {}: {}", epubPath.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ResolvedPosition> resolveFromTextPhrase(
            Path epubPath, String href, String textBefore, String textHighlight, String textAfter) {

        if (href == null || href.isBlank()) return Optional.empty();
        if (textHighlight == null || textHighlight.isBlank()) return Optional.empty();

        try {
            File epubFile = epubPath.toFile();
            List<String> allHrefs = EpubContentReader.getAllSpineItemHrefs(epubFile);

            int spineIndex = findSpineIndexByHref(allHrefs, href);
            if (spineIndex < 0) {
                log.debug("href {} not found in spine of {}", href, epubPath.getFileName());
                return Optional.empty();
            }

            String plainText = extractPlainText(epubFile, spineIndex);
            if (plainText.isEmpty()) return Optional.empty();

            int matchOffset = findBestPhraseMatch(plainText, textBefore, textHighlight, textAfter);
            if (matchOffset < 0) {
                log.debug("Text phrase not found in spine item {} of {}", href, epubPath.getFileName());
                return Optional.empty();
            }

            float progression = clamp((float) matchOffset / (float) plainText.length());
            return Optional.of(new ResolvedPosition(href, progression));

        } catch (Exception e) {
            log.debug("Text phrase resolution failed for {}: {}", epubPath.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    private int findBestPhraseMatch(String plainText, String textBefore, String textHighlight, String textAfter) {
        String normalizedText = normalizeWhitespace(plainText);
        String normalizedHighlight = normalizeWhitespace(textHighlight);

        if (normalizedHighlight.isBlank()) return -1;

        int bestOffset = -1;
        int bestScore = -1;
        int searchFrom = 0;

        while (true) {
            int idx = normalizedText.indexOf(normalizedHighlight, searchFrom);
            if (idx < 0) break;

            int score = 0;
            if (textBefore != null && !textBefore.isBlank()) {
                String normalizedBefore = normalizeWhitespace(textBefore);
                String preceding = normalizedText.substring(Math.max(0, idx - normalizedBefore.length() - 20), idx);
                if (preceding.contains(normalizedBefore)) score += 2;
            }
            if (textAfter != null && !textAfter.isBlank()) {
                String normalizedAfter = normalizeWhitespace(textAfter);
                int afterStart = idx + normalizedHighlight.length();
                String following = normalizedText.substring(afterStart,
                        Math.min(normalizedText.length(), afterStart + normalizedAfter.length() + 20));
                if (following.contains(normalizedAfter)) score += 2;
            }

            if (score > bestScore) {
                bestScore = score;
                bestOffset = idx;
            }

            searchFrom = idx + 1;
        }

        return bestOffset;
    }

    private int findSpineIndexByHref(List<String> hrefs, String targetHref) {
        String normalizedTarget = stripPathPrefix(targetHref);
        for (int i = 0; i < hrefs.size(); i++) {
            String candidate = hrefs.get(i);
            if (candidate == null) continue;
            if (candidate.equals(targetHref) || stripPathPrefix(candidate).equals(normalizedTarget)) {
                return i;
            }
        }
        return -1;
    }

    private String stripPathPrefix(String href) {
        int lastSlash = href.lastIndexOf('/');
        return lastSlash >= 0 ? href.substring(lastSlash + 1) : href;
    }

    private String extractPlainText(File epubFile, int spineIndex) {
        String html = EpubContentReader.getSpineItemContent(epubFile, spineIndex);
        Document doc = Jsoup.parse(html);
        return doc.body().text();
    }

    private String normalizeWhitespace(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").trim();
    }

    private float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
