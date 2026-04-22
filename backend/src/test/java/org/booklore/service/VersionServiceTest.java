package org.booklore.service;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import org.booklore.model.dto.ReleaseNote;
import org.booklore.model.dto.VersionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class VersionServiceTest {

    private VersionService service;
    private VersionService spyService;

    @BeforeEach
    void setUp() {
        service = new VersionService();
        spyService = Mockito.spy(service);
    }


    @Nested
    class VersionComparison {

        private Method cmp;

        @BeforeEach
        void init() throws Exception {
            cmp = VersionService.class
                    .getDeclaredMethod("isVersionGreater", String.class, String.class);
            cmp.setAccessible(true);
        }

        @Test
        void returnsTrueWhenMajorIncreases() throws Exception {
            assertThat((Boolean) cmp.invoke(service, "2.0.0", "1.9.9"))
                    .isTrue();
        }

        @Test
        void returnsFalseWhenMajorDecreases() throws Exception {
            assertThat((Boolean) cmp.invoke(service, "1.0.0", "2.0.0"))
                    .isFalse();
        }

        @Test
        void returnsTrueForPatchIncrease() throws Exception {
            assertThat((Boolean) cmp.invoke(service, "1.0.1", "1.0.0"))
                    .isTrue();
        }

        @Test
        void returnsFalseForPatchDecrease() throws Exception {
            assertThat((Boolean) cmp.invoke(service, "1.0.0", "1.0.1"))
                    .isFalse();
        }

        @Test
        void returnsFalseWhenEqual() throws Exception {
            assertThat((Boolean) cmp.invoke(service, "1.2.3", "1.2.3"))
                    .isFalse();
        }

        @Test
        void handlesDifferentLengthVersions() throws Exception {
            assertThat((Boolean) cmp.invoke(service, "1.2.0", "1.1"))
                    .isTrue();
            assertThat((Boolean) cmp.invoke(service, "1.0", "1.0.1"))
                    .isFalse();
        }

        @Test
        void ignoresPrefixAndSafelyHandlesInvalid() throws Exception {
            assertThat((Boolean) cmp.invoke(service, "v1.10", "v1.9.9"))
                    .isTrue();
            assertThat((Boolean) cmp.invoke(service, "x.y", "1.0"))
                    .isFalse();
        }

        @Test
        void returnsFalseWhenVersion2IsNull() throws Exception {
            assertThat((Boolean) cmp.invoke(service, "1.0.0", null))
                    .isFalse();
        }

        @Test
        void returnsFalseWhenVersion1IsNull() throws Exception {
            assertThat((Boolean) cmp.invoke(service, null, "1.0.0"))
                    .isFalse();
        }

        @Test
        void returnsFalseWhenBothVersionsNull() throws Exception {
            assertThat((Boolean) cmp.invoke(service, null, null))
                    .isFalse();
        }
    }


    @Nested
    class GetVersionInfoTests {

        @Test
        void includesAppAndLatestOnSuccess() {
            Mockito.doReturn("v9.9.9")
                    .when(spyService)
                    .fetchLatestGitHubReleaseVersion();

            VersionInfo info = spyService.getVersionInfo();

            assertThat(info.getCurrent())
                    .isEqualTo(service.appVersion);
            assertThat(info.getLatest())
                    .isEqualTo("v9.9.9");
        }

        @Test
        void usesUnknownIfFetchFails() {
            Mockito.doThrow(new RuntimeException("fail"))
                    .when(spyService)
                    .fetchLatestGitHubReleaseVersion();

            VersionInfo info = spyService.getVersionInfo();

            assertThat(info.getLatest())
                    .isEqualTo("unknown");
        }
    }


    @Nested
    class GetChangelogSinceCurrentVersionTests {

        @Test
        void returnsNotesWhenAvailable() {
            LocalDateTime fixedTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0);
            ReleaseNote note = new ReleaseNote("v1.1", "n", "b", "u", fixedTime);

            Mockito.doReturn(List.of(note))
                    .when(spyService)
                    .fetchReleaseNotesSince(service.appVersion);

            List<ReleaseNote> result = spyService.getChangelogSinceCurrentVersion();
            assertThat(result).hasSize(1).containsExactly(note);
        }

        @Test
        void returnsEmptyListWhenNoNewReleases() {
            Mockito.doReturn(List.of())
                    .when(spyService)
                    .fetchReleaseNotesSince(service.appVersion);

            var result = spyService.getChangelogSinceCurrentVersion();
            assertThat(result).isEmpty();
        }
    }
}