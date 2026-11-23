package org.booklore.service.metadata.writer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NasSynologySyntheticProbe {
    private NasSynologySyntheticProbe() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 0) {
            throw new IllegalArgumentException("The synthetic probe accepts no arguments");
        }
        String capabilities;
        try (var lines = Files.lines(Path.of("/proc/self/status"))) {
            capabilities = lines.filter(line -> line.startsWith("CapEff:"))
                    .findFirst().orElseThrow(() -> new IOException("Missing effective capabilities"));
        }
        if (!capabilities.substring("CapEff:".length()).trim().equals("0000000000000001")) {
            throw new IOException("The synthetic probe requires only CAP_CHOWN");
        }
        PrintStream output = System.out;
        System.setOut(System.err);
        Path sandbox = Path.of("/sandbox");
        Path input = sandbox.resolve("input.epub");
        NasEpubPrototype.validateArchive(input);
        String original = NasEpubPrototype.sha256(input);
        var permissions = new NasSynologyPermissions();
        var expected = permissions.capture(input);
        var request = new NasEpubJobStore.JobRequest(UUID.randomUUID(), new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("input.epub", "prepared.epub", original,
                        "Synology synthetic test title", List.of("Synthetic author")))));
        var replacement = new NasEpubSyntheticReplacement(permissions);
        replacement.prepare(sandbox, request);
        try {
            replacement.apply(sandbox, request);
            if (NasEpubPrototype.sha256(input).equals(original)) {
                throw new IOException("Synthetic replacement did not change the file");
            }
            permissions.verify(input, expected);
        } catch (IOException | RuntimeException e) {
            try {
                replacement.rollback(sandbox, request);
            } catch (IOException | RuntimeException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        }
        replacement.rollback(sandbox, request);
        if (!NasEpubPrototype.sha256(input).equals(original)) {
            throw new IOException("Synthetic rollback did not restore the file");
        }
        permissions.verify(input, expected);
        output.println(new ObjectMapper().writeValueAsString(Map.of(
                "capChownOnly", true, "syntheticReplacementRestored", true,
                "ownershipModeAclPreserved", true, "operationId", request.operationId())));
    }
}
