package org.booklore.service.metadata.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.MoodEntity;
import org.booklore.model.entity.TagEntity;
import org.booklore.service.ArchiveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NasEpubPrototypeTest {
    @TempDir
    Path sandbox;

    @Test
    void updatesSyntheticCopyWithoutChangingInput() throws Exception {
        Path input = createEpub("input.epub");
        String originalHash = hash(input);

        ObjectMapper mapper = new ObjectMapper();
        byte[] request = mapper.writeValueAsBytes(new NasEpubPrototype.Request(
                "input.epub", "output.epub", originalHash, "New title", List.of("New author")));
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        NasEpubPrototype.run(new ByteArrayInputStream(request), response, sandbox);
        var result = mapper.readValue(response.toByteArray(), NasEpubPrototype.Result.class);

        assertThat(hash(input)).isEqualTo(originalHash);
        Path output = sandbox.resolve(result.output());
        assertThat(hash(output)).isEqualTo(result.outputSha256());
        try (ZipFile zip = new ZipFile(output.toFile())) {
            String opf = new String(zip.getInputStream(zip.getEntry("OPS/book.opf")).readAllBytes(), StandardCharsets.UTF_8);
            assertThat(opf).contains("New title", "New author", "Keep this description");
            assertThat(opf).doesNotContain("Old author");
        }
    }

    @Test
    void titleAndAuthorsKeepExistingBookloreMetadata() throws Exception {
        Path input = createEpub("input.epub", """
                <meta property="booklore:subtitle">Existing subtitle</meta>
                <meta property="booklore:page_count">321</meta>
                <meta property="booklore:future_field">Future value</meta>
                """);
        String originalHash = hash(input);
        NasEpubPrototype.process(sandbox, new NasEpubPrototype.Request(
                "input.epub", "output.epub", originalHash, "New title", List.of("New author")));

        assertThat(hash(input)).isEqualTo(originalHash);
        try (ZipFile zip = new ZipFile(sandbox.resolve("output.epub").toFile())) {
            String opf = new String(zip.getInputStream(zip.getEntry("OPS/book.opf")).readAllBytes(), StandardCharsets.UTF_8);
            assertThat(opf).contains("New title", "New author", "booklore:subtitle", "Existing subtitle",
                    "booklore:page_count", "321", "booklore:future_field", "Future value");
        }
    }

    @Test
    void fullMetadataMatchesLocalWriterIncludingLocksAndClearFlags() throws Exception {
        Path input = createEpub("input.epub");
        Path local = sandbox.resolve("local.epub");
        Files.copy(input, local);
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Locked title").titleLocked(true)
                .authors(List.of(AuthorEntity.builder().name("New author").build()))
                .categories(Set.of(CategoryEntity.builder().name("Fantasy").build()))
                .moods(Set.of(MoodEntity.builder().name("Hopeful").build()))
                .tags(Set.of(TagEntity.builder().name("Adventure").build()))
                .publishedDate(LocalDate.of(2020, 3, 5))
                .language("fr").isbn13("9781234567890")
                .seriesName("Series").seriesNumber(2f).pageCount(120)
                .build();
        MetadataClearFlags clear = new MetadataClearFlags();
        clear.setDescription(true);

        EpubMetadataWriter localWriter = new EpubMetadataWriter(MetadataPersistenceSettings::new, new ArchiveService());
        localWriter.writeMetadataToFile(local.toFile(), metadata, clear);
        var request = new NasEpubPrototype.Request("input.epub", "output.epub", hash(input), null, null,
                NasEpubMetadataMapper.INSTANCE.snapshot(metadata), clear, null);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        NasEpubPrototype.run(new ByteArrayInputStream(mapper.writeValueAsBytes(request)), response, sandbox);

        try (ZipFile expected = new ZipFile(local.toFile());
             ZipFile actual = new ZipFile(sandbox.resolve("output.epub").toFile())) {
            String expectedOpf = new String(expected.getInputStream(expected.getEntry("OPS/book.opf")).readAllBytes(), StandardCharsets.UTF_8);
            String actualOpf = new String(actual.getInputStream(actual.getEntry("OPS/book.opf")).readAllBytes(), StandardCharsets.UTF_8);
            assertThat(actualOpf.replaceAll("(creator|collection)-[0-9a-f]{8}", "$1-id"))
                    .isEqualTo(expectedOpf.replaceAll("(creator|collection)-[0-9a-f]{8}", "$1-id"));
            assertThat(actualOpf)
                    .contains("Locked title", "New author", "Fantasy", "Hopeful", "Adventure", "2020-03-05")
                    .doesNotContain("Old title", "Keep this description");
        }
    }

    @Test
    void fullMetadataKeepsNullAndEmptyRelationsDistinct() throws Exception {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setAuthors(null);
        metadata.setCategories(Set.of());
        var snapshot = NasEpubMetadataMapper.INSTANCE.snapshot(metadata);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        BookMetadataEntity restored = NasEpubMetadataMapper.INSTANCE.restore(
                mapper.readValue(mapper.writeValueAsBytes(snapshot), org.booklore.model.dto.BookMetadata.class));
        assertThat(restored.getAuthors()).isNull();
        assertThat(restored.getCategories()).isEmpty();
    }

    @Test
    void updatesCoverOnSyntheticCopyAndRejectsOversizedCover() throws Exception {
        Path input = createEpub("input.epub");
        String originalHash = hash(input);
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        assertThat(ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", image)).isTrue();
        var metadata = NasEpubMetadataMapper.INSTANCE.snapshot(BookMetadataEntity.builder().title("Title").build());
        var request = new NasEpubPrototype.Request("input.epub", "output.epub", originalHash, null, null,
                metadata, null, image.toByteArray());
        NasEpubPrototype.process(sandbox, request);

        assertThat(hash(input)).isEqualTo(originalHash);
        try (ZipFile zip = new ZipFile(sandbox.resolve("output.epub").toFile())) {
            assertThat(zip.getEntry("OPS/cover.png")).isNotNull();
            assertThat(zip.getInputStream(zip.getEntry("OPS/cover.png")).readAllBytes()).isEqualTo(image.toByteArray());
        }

        var oversized = new NasEpubPrototype.Request("input.epub", "other.epub", originalHash, null, null,
                metadata, null, new byte[4 * 1024 * 1024 + 1]);
        assertThatThrownBy(() -> NasEpubPrototype.process(sandbox, oversized))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cover");
        assertThat(Files.exists(sandbox.resolve("other.epub"))).isFalse();
    }

    @Test
    void batchRemovesPreparedOutputsOnFailureWithoutChangingInputs() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var firstRequest = new NasEpubPrototype.Request("first.epub", "first-result.epub", firstHash, "New", null);
        var badSecond = new NasEpubPrototype.Request("second.epub", "second-result.epub", "0".repeat(64), "New", null);

        assertThatThrownBy(() -> NasEpubPrototype.processBatch(sandbox,
                new NasEpubPrototype.BatchRequest(List.of(firstRequest, badSecond))))
                .isInstanceOf(IOException.class).hasMessageContaining("SHA-256");
        assertThat(Files.exists(sandbox.resolve("first-result.epub"))).isFalse();
        assertThat(Files.exists(sandbox.resolve("second-result.epub"))).isFalse();
        assertThat(hash(first)).isEqualTo(firstHash);
        assertThat(hash(second)).isEqualTo(secondHash);
    }

    @Test
    void batchRefusesCollisionsAndPreservesExistingOutputs() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var collision = new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("first.epub", "SECOND.epub", firstHash, "New", null),
                new NasEpubPrototype.Request("second.epub", "out.epub", secondHash, "New", null)));
        assertThatThrownBy(() -> NasEpubPrototype.processBatch(sandbox, collision))
                .isInstanceOf(IllegalArgumentException.class);
        Path existing = sandbox.resolve("existing.epub");
        Files.writeString(existing, "preserve");
        var existingOutput = new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("first.epub", "created.epub", firstHash, "New", null),
                new NasEpubPrototype.Request("second.epub", "existing.epub", secondHash, "New", null)));
        assertThatThrownBy(() -> NasEpubPrototype.processBatch(sandbox, existingOutput))
                .isInstanceOf(IOException.class);
        assertThat(Files.exists(sandbox.resolve("created.epub"))).isFalse();
        assertThat(Files.readString(existing)).isEqualTo("preserve");
        assertThat(hash(first)).isEqualTo(firstHash);
        assertThat(hash(second)).isEqualTo(secondHash);
    }

    @Test
    void batchRejectsEmptyOrOversizedRequests() throws Exception {
        var empty = new NasEpubPrototype.BatchRequest(List.of());
        var request = new NasEpubPrototype.Request("first.epub", "output.epub", "0".repeat(64), "New", null);
        var oversized = new NasEpubPrototype.BatchRequest(java.util.Collections.nCopies(101, request));
        assertThatThrownBy(() -> NasEpubPrototype.processBatch(sandbox, empty))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NasEpubPrototype.processBatch(sandbox, oversized))
                .isInstanceOf(IllegalArgumentException.class);
        try (var files = Files.list(sandbox)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void durableJobCanBeRetriedAfterInterruptionAndRejectsChangedRequests() throws Exception {
        Path input = createEpub("input.epub");
        String inputHash = hash(input);
        UUID id = UUID.randomUUID();
        var item = new NasEpubPrototype.Request("input.epub", "output.epub", inputHash, "New", null);
        var request = new NasEpubJobStore.JobRequest(id, new NasEpubPrototype.BatchRequest(List.of(item)));
        var store = new NasEpubJobStore();

        var completed = store.execute(sandbox, request);
        Path job = sandbox.resolve(".grimmory-synthetic-jobs").resolve(id.toString());
        assertThat(store.execute(sandbox, request)).isEqualTo(completed);
        assertThat(hash(input)).isEqualTo(inputHash);
        var changed = new NasEpubJobStore.JobRequest(id, new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("input.epub", "output.epub", inputHash, "Different", null))));
        assertThatThrownBy(() -> store.execute(sandbox, changed))
                .isInstanceOf(IOException.class).hasMessageContaining("different request");

        Files.delete(job.resolve("result.json"));
        Files.writeString(job.resolve("output.epub"), "partial output");
        var recovered = new NasEpubJobStore().execute(sandbox, request);
        assertThat(recovered.batch().results()).hasSize(1);
        assertThat(hash(job.resolve("output.epub"))).isEqualTo(recovered.batch().results().getFirst().outputSha256());
        assertThat(hash(input)).isEqualTo(inputHash);

        Files.writeString(job.resolve("output.epub"), "corrupted output");
        assertThatThrownBy(() -> store.execute(sandbox, request))
                .isInstanceOf(IOException.class).hasMessageContaining("missing or modified");
        assertThat(hash(input)).isEqualTo(inputHash);
    }

    @Test
    void simultaneousAttemptsForSameJobReturnTheSameResult() throws Exception {
        Path input = createEpub("input.epub");
        String inputHash = hash(input);
        var item = new NasEpubPrototype.Request("input.epub", "output.epub", inputHash, "New", null);
        var request = new NasEpubJobStore.JobRequest(UUID.randomUUID(), new NasEpubPrototype.BatchRequest(List.of(item)));
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                return new NasEpubJobStore().execute(sandbox, request);
            });
            var second = pool.submit(() -> {
                start.await();
                return new NasEpubJobStore().execute(sandbox, request);
            });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(hash(input)).isEqualTo(inputHash);
    }

    @Test
    void separateStateDirectoryRetainsBackupsAcrossRestart(@TempDir Path state) throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var request = replacementRequest(firstHash, secondHash);
        var replacement = new NasEpubSyntheticReplacement(null, null, new NasEpubJobStore(state));

        assertThat(replacement.prepare(sandbox, request)).isEqualTo(NasEpubSyntheticReplacement.State.PREPARED);
        Path job = state.resolve(request.operationId().toString());
        assertThat(Files.exists(job.resolve("replacement").resolve("0.bak"))).isTrue();
        assertThat(Files.exists(sandbox.resolve(".grimmory-synthetic-jobs").resolve(request.operationId().toString()))).isFalse();
        assertThat(replacement.apply(sandbox, request)).isEqualTo(NasEpubSyntheticReplacement.State.APPLIED);

        var restarted = new NasEpubSyntheticReplacement(null, null, new NasEpubJobStore(state));
        assertThat(restarted.rollback(sandbox, request)).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
        assertThat(hash(first)).isEqualTo(firstHash);
        assertThat(hash(second)).isEqualTo(secondHash);
        assertThat(Files.exists(job.resolve("replacement").resolve("0.bak"))).isTrue();
    }

    @Test
    void separateStateRejectsLocationChangesAndMissingBinding(@TempDir Path state, @TempDir Path otherState) throws Exception {
        Path input = createEpub("input.epub");
        var request = new NasEpubJobStore.JobRequest(UUID.randomUUID(), new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("input.epub", "output.epub", hash(input), "New", null))));
        new NasEpubJobStore(state).execute(sandbox, request);

        assertThatThrownBy(() -> new NasEpubJobStore().execute(sandbox, request))
                .isInstanceOf(IOException.class).hasMessageContaining("location changed");
        assertThatThrownBy(() -> new NasEpubJobStore(otherState).execute(sandbox, request))
                .isInstanceOf(IOException.class).hasMessageContaining("location changed");
        Files.delete(state.resolve("sandbox-binding"));
        assertThatThrownBy(() -> new NasEpubJobStore(state).execute(sandbox, request))
                .isInstanceOf(IOException.class).hasMessageContaining("binding is missing");
    }

    @Test
    void separateStateRejectsOtherSandboxesAndLegacyJobs(@TempDir Path state, @TempDir Path otherSandbox,
                                                         @TempDir Path legacySandbox, @TempDir Path anotherState) throws Exception {
        new NasEpubJobStore(state).storageRoot(sandbox.toRealPath());
        assertThatThrownBy(() -> new NasEpubJobStore(state).storageRoot(otherSandbox.toRealPath()))
                .isInstanceOf(IOException.class).hasMessageContaining("another sandbox");

        Path legacy = Files.createDirectory(legacySandbox.resolve(".grimmory-synthetic-jobs"));
        Files.createDirectory(legacy.resolve("old-job"));
        assertThatThrownBy(() -> new NasEpubJobStore(anotherState).storageRoot(legacySandbox.toRealPath()))
                .isInstanceOf(IOException.class).hasMessageContaining("cannot be moved");
    }

    @Test
    void separateStateRejectsMissingLocationMarker(@TempDir Path state) throws Exception {
        new NasEpubJobStore(state).storageRoot(sandbox.toRealPath());
        Files.delete(sandbox.resolve(".grimmory-synthetic-jobs").resolve("storage-location"));
        assertThatThrownBy(() -> new NasEpubJobStore(state).storageRoot(sandbox.toRealPath()))
                .isInstanceOf(IOException.class).hasMessageContaining("location marker is missing");
        assertThatThrownBy(() -> new NasEpubJobStore().storageRoot(sandbox.toRealPath()))
                .isInstanceOf(IOException.class).hasMessageContaining("location marker is missing");
    }

    @Test
    void separateStateMustExistOutsideSandbox() throws Exception {
        Path nested = Files.createDirectory(sandbox.resolve("nested"));
        assertThatThrownBy(() -> new NasEpubJobStore(nested).storageRoot(sandbox.toRealPath()))
                .isInstanceOf(IOException.class).hasMessageContaining("separate from the sandbox");
        assertThatThrownBy(() -> new NasEpubJobStore(sandbox.resolve("missing")).storageRoot(sandbox.toRealPath()))
                .isInstanceOf(IOException.class).hasMessageContaining("existing absolute directory");
    }

    @Test
    void syntheticReplacementCanBeAppliedAndRolledBackIdempotently() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var request = replacementRequest(firstHash, secondHash);
        var replacement = new NasEpubSyntheticReplacement();

        assertThat(replacement.prepare(sandbox, request)).isEqualTo(NasEpubSyntheticReplacement.State.PREPARED);
        assertThat(hash(first)).isEqualTo(firstHash);
        assertThat(hash(second)).isEqualTo(secondHash);
        assertThat(replacement.apply(sandbox, request)).isEqualTo(NasEpubSyntheticReplacement.State.APPLIED);
        assertThat(hash(first)).isNotEqualTo(firstHash);
        assertThat(hash(second)).isNotEqualTo(secondHash);
        assertThat(replacement.apply(sandbox, request)).isEqualTo(NasEpubSyntheticReplacement.State.APPLIED);
        assertThat(replacement.rollback(sandbox, request)).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
        assertThat(replacement.rollback(sandbox, request)).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
        assertThat(hash(first)).isEqualTo(firstHash);
        assertThat(hash(second)).isEqualTo(secondHash);
        assertThatThrownBy(() -> replacement.apply(sandbox, request))
                .isInstanceOf(IOException.class).hasMessageContaining("cannot be applied");
    }

    @Test
    void syntheticReplacementRestoresAfterInterruptedApplyEvenWhenCandidateIsLost() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var request = replacementRequest(firstHash, secondHash);
        new NasEpubSyntheticReplacement().prepare(sandbox, request);
        Path job = sandbox.resolve(".grimmory-synthetic-jobs").resolve(request.operationId().toString());
        Path journal = job.resolve("replacement").resolve("journal.json");
        Files.writeString(journal, Files.readString(journal).replace("\"PREPARED\"", "\"APPLYING\""));
        Files.copy(job.resolve("first-result.epub"), first, StandardCopyOption.REPLACE_EXISTING);
        Files.delete(job.resolve("first-result.epub"));

        assertThat(new NasEpubSyntheticReplacement().rollback(sandbox, request))
                .isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
        assertThat(hash(first)).isEqualTo(firstHash);
        assertThat(hash(second)).isEqualTo(secondHash);
    }

    @Test
    void syntheticReplacementResumesAnInterruptedRollback() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var request = replacementRequest(firstHash, secondHash);
        var replacement = new NasEpubSyntheticReplacement();
        replacement.prepare(sandbox, request);
        replacement.apply(sandbox, request);
        Path directory = sandbox.resolve(".grimmory-synthetic-jobs")
                .resolve(request.operationId().toString()).resolve("replacement");
        Path journal = directory.resolve("journal.json");
        Files.writeString(journal, Files.readString(journal).replace("\"APPLIED\"", "\"ROLLING_BACK\""));
        Files.copy(directory.resolve("0.bak"), first, StandardCopyOption.REPLACE_EXISTING);

        assertThat(new NasEpubSyntheticReplacement().rollback(sandbox, request))
                .isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
        assertThat(hash(first)).isEqualTo(firstHash);
        assertThat(hash(second)).isEqualTo(secondHash);
    }

    @Test
    void syntheticReplacementResumesAnInterruptedApply() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        var request = replacementRequest(hash(first), hash(second));
        var replacement = new NasEpubSyntheticReplacement();
        replacement.prepare(sandbox, request);
        Path job = sandbox.resolve(".grimmory-synthetic-jobs").resolve(request.operationId().toString());
        Path journal = job.resolve("replacement").resolve("journal.json");
        Files.writeString(journal, Files.readString(journal).replace("\"PREPARED\"", "\"APPLYING\""));
        Files.copy(job.resolve("first-result.epub"), first, StandardCopyOption.REPLACE_EXISTING);

        assertThat(new NasEpubSyntheticReplacement().apply(sandbox, request))
                .isEqualTo(NasEpubSyntheticReplacement.State.APPLIED);
        assertThat(hash(first)).isEqualTo(hash(job.resolve("first-result.epub")));
        assertThat(hash(second)).isEqualTo(hash(job.resolve("second-result.epub")));
    }

    @Test
    void syntheticReplacementRollsBackEarlierFilesWhenCandidateIsCorrupted() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var request = replacementRequest(firstHash, secondHash);
        var replacement = new NasEpubSyntheticReplacement();
        replacement.prepare(sandbox, request);
        Path job = sandbox.resolve(".grimmory-synthetic-jobs").resolve(request.operationId().toString());
        Files.writeString(job.resolve("second-result.epub"), "corrupted candidate");

        assertThatThrownBy(() -> replacement.apply(sandbox, request))
                .isInstanceOf(IOException.class).hasMessageContaining("modified");
        assertThat(hash(first)).isEqualTo(firstHash);
        assertThat(hash(second)).isEqualTo(secondHash);
        assertThat(replacement.rollback(sandbox, request)).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
    }

    @Test
    void syntheticReservationBlocksOtherJobsUntilRollbackOrFinalization() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var firstJob = replacementRequest(firstHash, secondHash);
        var staleJob = replacementRequest(firstHash, secondHash);
        var replacement = new NasEpubSyntheticReplacement();
        replacement.prepare(sandbox, firstJob);
        assertThatThrownBy(() -> replacement.prepare(sandbox, staleJob))
                .isInstanceOf(IOException.class).hasMessageContaining("reserved");
        replacement.apply(sandbox, firstJob);
        String appliedFirst = hash(first);
        String appliedSecond = hash(second);
        assertThatThrownBy(() -> replacement.prepare(sandbox, staleJob))
                .isInstanceOf(IOException.class).hasMessageContaining("reserved");
        assertThat(replacement.finish(sandbox, firstJob)).isEqualTo(NasEpubSyntheticReplacement.State.COMMITTED);
        assertThatThrownBy(() -> replacement.rollback(sandbox, firstJob))
                .isInstanceOf(IOException.class).hasMessageContaining("cannot be rolled back");
        assertThatThrownBy(() -> replacement.prepare(sandbox, staleJob)).isInstanceOf(IOException.class);
        var next = replacementRequest(appliedFirst, appliedSecond);
        assertThat(replacement.prepare(sandbox, next)).isEqualTo(NasEpubSyntheticReplacement.State.PREPARED);
        assertThat(replacement.finish(sandbox, firstJob)).isEqualTo(NasEpubSyntheticReplacement.State.COMMITTED);
        assertThat(hash(first)).isEqualTo(appliedFirst);
        assertThat(hash(second)).isEqualTo(appliedSecond);
        assertThat(replacement.rollback(sandbox, next)).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
    }

    @Test
    void syntheticReplacementStopsIfItsDurableReservationDisappears() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var request = replacementRequest(firstHash, secondHash);
        var replacement = new NasEpubSyntheticReplacement();
        replacement.prepare(sandbox, request);
        Path reservation = sandbox.resolve(".grimmory-synthetic-jobs").resolve("replacement-reservation");
        Files.delete(reservation);
        assertThatThrownBy(() -> replacement.apply(sandbox, request))
                .isInstanceOf(IOException.class).hasMessageContaining("reservation");
        assertThatThrownBy(() -> replacement.prepare(sandbox, request))
                .isInstanceOf(IOException.class).hasMessageContaining("reservation");
        assertThat(hash(first)).isEqualTo(firstHash);
        assertThat(hash(second)).isEqualTo(secondHash);
        Files.writeString(reservation, request.operationId().toString());
        assertThat(replacement.rollback(sandbox, request)).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
    }

    @Test
    void syntheticReplacementPreservesRecordedAclAcrossApplyAndRollback() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var fake = new FakeSynologyPermissions();
        fake.set(first, new NasSynologyPermissions.Identity(1031, 100, 0777),
                "Archive: is_inherit,is_support_ACL", "inherited ACL");
        fake.set(second, new NasSynologyPermissions.Identity(1035, 101, 0777),
                "Archive: has_ACL,is_support_ACL", "explicit ACL");
        var firstPermissions = fake.permissions.capture(first);
        var secondPermissions = fake.permissions.capture(second);
        var request = replacementRequest(firstHash, secondHash);
        var replacement = new NasEpubSyntheticReplacement(fake.permissions);
        replacement.prepare(sandbox, request);
        assertThatThrownBy(() -> new NasEpubSyntheticReplacement().apply(sandbox, request))
                .isInstanceOf(IOException.class).hasMessageContaining("journal");

        assertThat(new NasEpubSyntheticReplacement(fake.permissions).apply(sandbox, request))
                .isEqualTo(NasEpubSyntheticReplacement.State.APPLIED);
        assertThat(fake.ownershipSetCalls).isZero();
        assertThat(fake.permissions.capture(first)).isEqualTo(firstPermissions);
        assertThat(fake.permissions.capture(second)).isEqualTo(secondPermissions);
        assertThat(new NasEpubSyntheticReplacement(fake.permissions).rollback(sandbox, request))
                .isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
        assertThat(hash(first)).isEqualTo(firstHash);
        assertThat(hash(second)).isEqualTo(secondHash);
        assertThat(fake.permissions.capture(first)).isEqualTo(firstPermissions);
        assertThat(fake.permissions.capture(second)).isEqualTo(secondPermissions);
    }

    @Test
    void syntheticRollbackDoesNotOverwriteAnUnexpectedAclChange() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var fake = new FakeSynologyPermissions();
        fake.set(first, new NasSynologyPermissions.Identity(1031, 100, 0777),
                "Archive: is_inherit,is_support_ACL", "inherited ACL");
        fake.set(second, new NasSynologyPermissions.Identity(1035, 101, 0777),
                "Archive: has_ACL,is_support_ACL", "explicit ACL");
        var originalPermissions = fake.permissions.capture(first);
        var request = replacementRequest(firstHash, secondHash);
        var replacement = new NasEpubSyntheticReplacement(fake.permissions);
        replacement.prepare(sandbox, request);
        replacement.apply(sandbox, request);
        String changedContent = hash(first);
        fake.set(first, originalPermissions.identity(), originalPermissions.archive(), "unexpected ACL");

        assertThatThrownBy(() -> replacement.rollback(sandbox, request))
                .isInstanceOf(IOException.class).hasMessageContaining("ACL changed");
        assertThat(hash(first)).isEqualTo(changedContent);
        assertThat(hash(second)).isEqualTo(secondHash);
        fake.set(first, originalPermissions.identity(), originalPermissions.archive(), originalPermissions.entries());
        assertThat(replacement.rollback(sandbox, request)).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
        assertThat(hash(first)).isEqualTo(firstHash);
    }

    @Test
    void syntheticReplacementStopsWhenAclCopyFails() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var fake = new FakeSynologyPermissions();
        fake.set(first, new NasSynologyPermissions.Identity(1031, 100, 0777),
                "Archive: is_inherit,is_support_ACL", "inherited ACL");
        fake.set(second, new NasSynologyPermissions.Identity(1035, 101, 0777),
                "Archive: has_ACL,is_support_ACL", "explicit ACL");
        var request = replacementRequest(firstHash, secondHash);
        var replacement = new NasEpubSyntheticReplacement(fake.permissions);
        replacement.prepare(sandbox, request);
        fake.failCopy = true;

        assertThatThrownBy(() -> replacement.apply(sandbox, request))
                .isInstanceOf(IOException.class).hasMessageContaining("Simulated ACL copy failure");
        assertThat(hash(first)).isEqualTo(firstHash);
        assertThat(hash(second)).isEqualTo(secondHash);
        assertThat(replacement.rollback(sandbox, request)).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
    }

    @Test
    void syntheticReplacementRecoversAfterAbruptInterruptionWithAcl() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var fake = new FakeSynologyPermissions();
        fake.set(first, new NasSynologyPermissions.Identity(1031, 100, 0777),
                "Archive: is_inherit,is_support_ACL", "inherited ACL");
        fake.set(second, new NasSynologyPermissions.Identity(1035, 101, 0777),
                "Archive: has_ACL,is_support_ACL", "explicit ACL");
        var firstPermissions = fake.permissions.capture(first);
        var secondPermissions = fake.permissions.capture(second);
        var request = replacementRequest(firstHash, secondHash);
        var replacement = new NasEpubSyntheticReplacement(fake.permissions,
                () -> { throw new AssertionError("Simulated abrupt interruption"); });
        replacement.prepare(sandbox, request);
        assertThatThrownBy(() -> replacement.apply(sandbox, request))
                .isInstanceOf(AssertionError.class).hasMessageContaining("abrupt interruption");
        assertThat(hash(first)).isNotEqualTo(firstHash);
        assertThat(hash(second)).isEqualTo(secondHash);
        assertThat(new NasEpubSyntheticReplacement(fake.permissions).rollback(sandbox, request))
                .isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
        assertThat(hash(first)).isEqualTo(firstHash);
        assertThat(hash(second)).isEqualTo(secondHash);
        assertThat(fake.permissions.capture(first)).isEqualTo(firstPermissions);
        assertThat(fake.permissions.capture(second)).isEqualTo(secondPermissions);
    }

    @Test
    void syntheticFinalizationCanReleaseAReservationAfterInterruption() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        var request = replacementRequest(hash(first), hash(second));
        var replacement = new NasEpubSyntheticReplacement();
        replacement.prepare(sandbox, request);
        replacement.apply(sandbox, request);
        replacement.finish(sandbox, request);
        Path reservation = sandbox.resolve(".grimmory-synthetic-jobs").resolve("replacement-reservation");
        Files.writeString(reservation, request.operationId().toString());
        assertThat(new NasEpubSyntheticReplacement().finish(sandbox, request))
                .isEqualTo(NasEpubSyntheticReplacement.State.COMMITTED);
        assertThat(Files.exists(reservation)).isFalse();
    }

    @Test
    void syntheticReplacementNeverOverwritesUnexpectedChangesOrUsesCorruptBackups() throws Exception {
        Path first = createEpub("first.epub");
        Path second = createEpub("second.epub");
        String firstHash = hash(first);
        String secondHash = hash(second);
        var request = replacementRequest(firstHash, secondHash);
        var replacement = new NasEpubSyntheticReplacement();
        replacement.prepare(sandbox, request);
        replacement.apply(sandbox, request);
        Path job = sandbox.resolve(".grimmory-synthetic-jobs").resolve(request.operationId().toString());
        byte[] candidate = Files.readAllBytes(first);
        Files.writeString(first, "unexpected change");

        assertThatThrownBy(() -> replacement.rollback(sandbox, request))
                .isInstanceOf(IOException.class).hasMessageContaining("refusing to overwrite");
        assertThat(Files.readString(first)).isEqualTo("unexpected change");
        assertThat(hash(second)).isEqualTo(secondHash);
        Files.write(first, candidate);
        Path backup = job.resolve("replacement").resolve("1.bak");
        byte[] backupContent = Files.readAllBytes(backup);
        Files.writeString(backup, "corrupted backup");
        assertThatThrownBy(() -> replacement.rollback(sandbox, request))
                .isInstanceOf(IOException.class).hasMessageContaining("modified");
        assertThat(Files.readAllBytes(first)).isEqualTo(candidate);
        assertThat(hash(second)).isEqualTo(secondHash);
        Files.write(backup, backupContent);
        assertThat(replacement.rollback(sandbox, request)).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
        assertThat(hash(first)).isEqualTo(firstHash);
    }

    private NasEpubJobStore.JobRequest replacementRequest(String firstHash, String secondHash) {
        return new NasEpubJobStore.JobRequest(UUID.randomUUID(), new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("first.epub", "first-result.epub", firstHash, "New first", null),
                new NasEpubPrototype.Request("second.epub", "second-result.epub", secondHash, "New second", null))));
    }

    @Test
    void refusesWrongHashAndExistingOutput() throws Exception {
        Path input = createEpub("input.epub");
        String hash = hash(input);
        var wrongHash = new NasEpubPrototype.Request("input.epub", "output.epub", "0".repeat(64), "New title", null);
        assertThatThrownBy(() -> NasEpubPrototype.process(sandbox, wrongHash))
                .isInstanceOf(IOException.class).hasMessageContaining("SHA-256");
        assertThat(Files.exists(sandbox.resolve("output.epub"))).isFalse();
        Files.writeString(sandbox.resolve("output.epub"), "existing");
        var existing = new NasEpubPrototype.Request("input.epub", "output.epub", hash, "New title", null);
        assertThatThrownBy(() -> NasEpubPrototype.process(sandbox, existing))
                .isInstanceOf(IOException.class);
        assertThat(Files.readString(sandbox.resolve("output.epub"))).isEqualTo("existing");
        assertThat(hash(input)).isEqualTo(hash);
    }

    @Test
    void refusesTraversalAndInvalidArchives() throws Exception {
        Path input = sandbox.resolve("bad.epub");
        Files.writeString(input, "not an EPUB");
        var traversal = new NasEpubPrototype.Request("../bad.epub", "output.epub", hash(input), "New title", null);
        assertThatThrownBy(() -> NasEpubPrototype.process(sandbox, traversal))
                .isInstanceOf(IllegalArgumentException.class);
        var invalid = new NasEpubPrototype.Request("bad.epub", "output.epub", hash(input), "New title", null);
        assertThatThrownBy(() -> NasEpubPrototype.process(sandbox, invalid))
                .isInstanceOf(IOException.class);
        assertThat(Files.exists(sandbox.resolve("output.epub"))).isFalse();
    }

    @Test
    void refusesUnmarkedEpubAndReportsWriterFailure() throws Exception {
        Path unmarked = sandbox.resolve("unmarked.epub");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(unmarked))) {
            entry(zip, "mimetype", "application/epub+zip");
        }
        var noMarker = new NasEpubPrototype.Request("unmarked.epub", "output.epub", hash(unmarked), "New title", null);
        assertThatThrownBy(() -> NasEpubPrototype.process(sandbox, noMarker))
                .isInstanceOf(IOException.class).hasMessageContaining("synthetic");

        Path malformed = sandbox.resolve("malformed.epub");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(malformed))) {
            entry(zip, "META-INF/grimmory-nas-prototype.txt", "synthetic-test-only-v1");
        }
        var badContent = new NasEpubPrototype.Request("malformed.epub", "output.epub", hash(malformed), "New title", null);
        assertThatThrownBy(() -> NasEpubPrototype.process(sandbox, badContent))
                .isInstanceOf(IOException.class).hasMessageContaining("Failed to write");
        assertThat(Files.exists(sandbox.resolve("output.epub"))).isFalse();
    }

    private Path createEpub(String name) throws IOException {
        return createEpub(name, "");
    }

    private Path createEpub(String name, String extraMetadata) throws IOException {
        Path path = sandbox.resolve(name);
        try (OutputStream out = Files.newOutputStream(path); ZipOutputStream zip = new ZipOutputStream(out)) {
            entry(zip, "mimetype", "application/epub+zip");
            entry(zip, "META-INF/grimmory-nas-prototype.txt", "synthetic-test-only-v1");
            entry(zip, "META-INF/test-name.txt", name);
            entry(zip, "META-INF/container.xml", """
                    <?xml version="1.0"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                      <rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>""");
            entry(zip, "OPS/book.opf", """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0"
                             prefix="booklore: http://booklore.org/metadata/1.0/">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Old title</dc:title>
                        <dc:creator>Old author</dc:creator>
                        <dc:description>Keep this description</dc:description>
                        %s
                      </metadata><manifest/><spine/>
                    </package>""".formatted(extraMetadata));
        }
        return path;
    }

    private void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static final class FakeSynologyPermissions {
        private final Map<Object, NasSynologyPermissions.Snapshot> files = new HashMap<>();
        private boolean failCopy;
        private int ownershipSetCalls;
        private final NasSynologyPermissions permissions = new NasSynologyPermissions(this::command,
                new NasSynologyPermissions.Ownership() {
                    @Override
                    public NasSynologyPermissions.Identity read(Path path) throws IOException {
                        return get(path).identity();
                    }

                    @Override
                    public void set(Path path, NasSynologyPermissions.Identity identity) throws IOException {
                        ownershipSetCalls++;
                        var current = get(path);
                        files.put(key(path), new NasSynologyPermissions.Snapshot(
                                new NasSynologyPermissions.Identity(identity.uid(), identity.gid(), current.identity().mode()),
                                current.archive(), current.entries()));
                    }
                });

        private void set(Path path, NasSynologyPermissions.Identity identity, String archive, String entries)
                throws IOException {
            files.put(key(path), new NasSynologyPermissions.Snapshot(identity, archive, entries));
        }

        private NasSynologyPermissions.Snapshot get(Path path) throws IOException {
            return files.getOrDefault(key(path), new NasSynologyPermissions.Snapshot(
                    new NasSynologyPermissions.Identity(0, 100, 0700),
                    "Archive: is_inherit,is_support_ACL", "default ACL"));
        }

        private String command(String action, Path... paths) throws IOException {
            return switch (action) {
                case "-get" -> get(paths[0]).archive() + "\n";
                case "-getace" -> get(paths[0]).entries() + "\n";
                case "-copy" -> {
                    if (failCopy) {
                        throw new IOException("Simulated ACL copy failure");
                    }
                    var source = get(paths[0]);
                    files.put(key(paths[1]), new NasSynologyPermissions.Snapshot(
                            source.identity(), source.archive(), source.entries()));
                    yield "";
                }
                default -> throw new IOException("Unexpected fake ACL command");
            };
        }

        private static Object key(Path path) throws IOException {
            return NasEpubPrototype.sha256(path);
        }
    }
}
