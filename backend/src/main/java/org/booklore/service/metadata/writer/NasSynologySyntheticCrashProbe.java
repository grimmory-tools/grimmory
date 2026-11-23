package org.booklore.service.metadata.writer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NasSynologySyntheticCrashProbe {
    private static final String ORIGINAL = "80693f33cf39d534264ae50eff72bbeb335fb2c1f39f4ec6049ad7245cc6b480";
    private static final UUID OPERATION_ID = UUID.fromString("239a5857-d3a4-4a56-857d-6d28680d9ce0");
    private static final Path SANDBOX = Path.of("/sandbox");

    private NasSynologySyntheticCrashProbe() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1 || !List.of("crash", "recover").contains(args[0])) {
            throw new IllegalArgumentException("Expected crash or recover");
        }
        try (var lines = Files.lines(Path.of("/proc/self/status"))) {
            String caps = lines.filter(line -> line.startsWith("CapEff:"))
                    .findFirst().orElseThrow(() -> new IOException("Missing effective capabilities"));
            if (!caps.substring("CapEff:".length()).trim().equals("0000000000000001")) {
                throw new IOException("The synthetic probe requires only CAP_CHOWN");
            }
        }
        PrintStream output = System.out;
        System.setOut(System.err);
        Path first = SANDBOX.resolve("first.epub");
        Path second = SANDBOX.resolve("second.epub");
        var permissions = new NasSynologyPermissions();
        var firstPermissions = permissions.capture(first);
        var secondPermissions = permissions.capture(second);
        if (firstPermissions.identity().uid() != 1031 || firstPermissions.identity().gid() != 100
                || secondPermissions.identity().uid() != 1035 || secondPermissions.identity().gid() != 101
                || !firstPermissions.archive().contains("is_inherit")
                || !secondPermissions.archive().contains("has_ACL")) {
            throw new IOException("Unexpected synthetic identities or ACL types");
        }
        var request = new NasEpubJobStore.JobRequest(OPERATION_ID, new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("first.epub", "first-output.epub", ORIGINAL, "Synthetic first", null),
                new NasEpubPrototype.Request("second.epub", "second-output.epub", ORIGINAL, "Synthetic second", null))));
        if ("crash".equals(args[0])) {
            if (!NasEpubPrototype.sha256(first).equals(ORIGINAL) || !NasEpubPrototype.sha256(second).equals(ORIGINAL)) {
                throw new IOException("Unexpected synthetic input hash");
            }
            var replacement = new NasEpubSyntheticReplacement(permissions, () -> Runtime.getRuntime().halt(77));
            replacement.prepare(SANDBOX, request);
            replacement.apply(SANDBOX, request);
            throw new IOException("Synthetic interruption did not occur");
        }
        if (NasEpubPrototype.sha256(first).equals(ORIGINAL) || !NasEpubPrototype.sha256(second).equals(ORIGINAL)) {
            throw new IOException("Synthetic interruption did not occur after the first replacement");
        }
        new NasEpubSyntheticReplacement(permissions).rollback(SANDBOX, request);
        if (!NasEpubPrototype.sha256(first).equals(ORIGINAL) || !NasEpubPrototype.sha256(second).equals(ORIGINAL)) {
            throw new IOException("Synthetic crash recovery did not restore the inputs");
        }
        permissions.verify(first, firstPermissions);
        permissions.verify(second, secondPermissions);
        output.println(new ObjectMapper().writeValueAsString(Map.of(
                "processCrashRecovered", true, "twoFilesRestored", true,
                "ownershipModeAclPreserved", true, "operationId", OPERATION_ID)));
    }
}
