package org.grimmory.service;

import lombok.extern.slf4j.Slf4j;
import org.grimmory.model.dto.ReleaseNote;
import org.grimmory.model.dto.VersionInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class VersionService {

    @Value("${app.version:unknown}")
    String appVersion;

    private static final String GITHUB_REPO = "grimmory-tools/grimmory";
    private static final String BASE_URI = "https://api.github.com/repos/" + GITHUB_REPO;
    private static final int MAX_RELEASES = 15;
    private static final RestClient REST_CLIENT = RestClient.builder()
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("User-Agent", "Grimmory-Version-Checker")
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();


    public VersionInfo getVersionInfo() {
        String latest = "unknown";
        try {
            latest = fetchLatestGitHubReleaseVersion();
        } catch (Exception e) {
            log.warn("Error fetching latest release version");
        }
        return new VersionInfo(appVersion, latest);
    }

    public List<ReleaseNote> getChangelogSinceCurrentVersion() {
        return fetchReleaseNotesSince(appVersion);
    }


    public String fetchLatestGitHubReleaseVersion() {
        try {
            String response = REST_CLIENT.get()
                    .uri(BASE_URI + "/releases/latest")
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(response);
            return root.path("tag_name").asText("unknown");

        } catch (Exception e) {
            log.warn("Failed to fetch latest release version");
            return "unknown";
        }
    }

    public List<ReleaseNote> fetchReleaseNotesSince(String currentVersion) {
        if ("development".equals(currentVersion)) {
            log.warn("Skipping fetch of release notes because current version is '{}', which is a local development build.", currentVersion);
            return new ArrayList<>();
        }
        log.info("Fetching release notes since version: {}", currentVersion);

        List<ReleaseNote> updates = new ArrayList<>();
        try {
            String response = REST_CLIENT.get()
                    .uri(BASE_URI + "/releases?per_page=" + MAX_RELEASES)
                    .retrieve()
                    .body(String.class);

            JsonNode releases = MAPPER.readTree(response);
            if (!releases.isArray()) {
                log.warn("Invalid releases response from GitHub API");
                return updates;
            }

            for (JsonNode release : releases) {
                String tag = release.path("tag_name").asText(null);
                if (tag == null || !isVersionGreater(tag, currentVersion)) {
                    continue;
                }
                String url = "https://github.com/grimmory-tools/grimmory" + "/releases/tag/" + tag;
                LocalDateTime published = LocalDateTime.parse(release.path("published_at").asText(), DateTimeFormatter.ISO_DATE_TIME);
                updates.add(new ReleaseNote(tag, release.path("name").asText(tag), release.path("body").asText(""), url, published));
            }

            log.info("Returning {} newer releases", updates.size());

        } catch (Exception e) {
            log.error("Failed to fetch release notes", e);
        }

        return updates;
    }

    private boolean isVersionGreater(String version1, String version2) {
        try {
            String[] v1 = version1.replace("v", "").split("\\.");
            String[] v2 = version2.replace("v", "").split("\\.");
            for (int i = 0; i < Math.max(v1.length, v2.length); i++) {
                int num1 = i < v1.length ? Integer.parseInt(v1[i]) : 0;
                int num2 = i < v2.length ? Integer.parseInt(v2[i]) : 0;
                if (num1 > num2) return true;
                if (num1 < num2) return false;
            }
        } catch (Exception e) {
            log.error("Version comparison failed: {}", e.getMessage());
        }
        return false;
    }
}
