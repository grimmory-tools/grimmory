package org.booklore.service.metadata.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpExchange;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class NasEpubAgent {
    private static final String ENDPOINT = "/v1/synthetic-epub";
    private static final String BATCH_ENDPOINT = "/v1/synthetic-batch";
    private static final String JOB_ENDPOINT = "/v1/synthetic-job";
    private static final String REPLACEMENT_PREPARE = "/v1/synthetic-replacement/prepare";
    private static final String REPLACEMENT_APPLY = "/v1/synthetic-replacement/apply";
    private static final String REPLACEMENT_ROLLBACK = "/v1/synthetic-replacement/rollback";
    private static final String REPLACEMENT_CANCEL_PREPARATION = "/v1/synthetic-replacement/cancel-preparation";
    private static final String REPLACEMENT_FINISH = "/v1/synthetic-replacement/finish";
    private static final Path SANDBOX = Path.of("/sandbox");
    private static final Path SECRETS = Path.of("/run/secrets");

    private NasEpubAgent() {}

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        if (args.length != 0) {
            throw new IllegalArgumentException("The agent accepts configuration only through its environment and secret files");
        }
        String bind = requiredEnvironment("NAS_EPUB_AGENT_BIND");
        int port = Integer.parseInt(requiredEnvironment("NAS_EPUB_AGENT_PORT"));
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid agent port");
        }
        char[] keyPassword = readPassword(SECRETS.resolve("keystore-password"));
        char[] trustPassword = readPassword(SECRETS.resolve("truststore-password"));
        SSLContext tls;
        try {
            tls = tlsContext(SECRETS.resolve("keystore.p12"), keyPassword,
                    SECRETS.resolve("truststore.p12"), trustPassword);
        } finally {
            Arrays.fill(keyPassword, '\0');
            Arrays.fill(trustPassword, '\0');
        }
        String stateDirectory = System.getenv("NAS_EPUB_AGENT_SYNTHETIC_STATE_DIR");
        if (stateDirectory != null && stateDirectory.isBlank()) {
            throw new IllegalArgumentException("Invalid NAS_EPUB_AGENT_SYNTHETIC_STATE_DIR value");
        }
        HttpsServer server = start(new InetSocketAddress(bind, port), tls, SANDBOX,
                syntheticReplacementEnabled(System.getenv("NAS_EPUB_AGENT_SYNTHETIC_REPLACEMENT")),
                booleanSetting("NAS_EPUB_AGENT_SYNTHETIC_SYNOLOGY_ACL",
                        System.getenv("NAS_EPUB_AGENT_SYNTHETIC_SYNOLOGY_ACL")),
                stateDirectory == null ? null : Path.of(stateDirectory));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        log.info("Synthetic EPUB agent listening on {}:{}", bind, port);
    }

    static HttpsServer start(InetSocketAddress bind, SSLContext tls, Path sandbox) throws IOException {
        return start(bind, tls, sandbox, false);
    }

    static HttpsServer start(InetSocketAddress bind, SSLContext tls, Path sandbox,
                             boolean syntheticReplacementEnabled) throws IOException {
        return start(bind, tls, sandbox, syntheticReplacementEnabled, false);
    }

    static HttpsServer start(InetSocketAddress bind, SSLContext tls, Path sandbox,
                             boolean syntheticReplacementEnabled, boolean syntheticSynologyAcl) throws IOException {
        return start(bind, tls, sandbox, syntheticReplacementEnabled, syntheticSynologyAcl, null);
    }

    static HttpsServer start(InetSocketAddress bind, SSLContext tls, Path sandbox,
                             boolean syntheticReplacementEnabled, boolean syntheticSynologyAcl,
                             Path stateDirectory) throws IOException {
        if (syntheticSynologyAcl && !syntheticReplacementEnabled) {
            throw new IllegalArgumentException("Synology ACL testing requires synthetic replacement");
        }
        Path root = sandbox.toRealPath();
        if (!Files.isDirectory(root)) {
            throw new IOException("Sandbox is not a directory");
        }
        NasEpubJobStore jobs = new NasEpubJobStore(stateDirectory);
        if (stateDirectory != null) {
            jobs.storageRoot(root);
        }
        NasEpubSyntheticReplacement replacement = new NasEpubSyntheticReplacement(
                syntheticSynologyAcl ? new NasSynologyPermissions() : null, null, jobs);
        HttpsServer server = HttpsServer.create(bind, 8);
        server.setHttpsConfigurator(new HttpsConfigurator(tls) {
            @Override
            public void configure(HttpsParameters parameters) {
                SSLParameters sslParameters = getSSLContext().getDefaultSSLParameters();
                sslParameters.setNeedClientAuth(true);
                parameters.setSSLParameters(sslParameters);
            }
        });
        server.createContext(ENDPOINT, exchange -> handle(exchange, ENDPOINT, root, Mode.SINGLE, jobs, replacement));
        server.createContext(BATCH_ENDPOINT, exchange -> handle(exchange, BATCH_ENDPOINT, root, Mode.BATCH, jobs, replacement));
        server.createContext(JOB_ENDPOINT, exchange -> handle(exchange, JOB_ENDPOINT, root, Mode.JOB, jobs, replacement));
        if (syntheticReplacementEnabled) {
            server.createContext(REPLACEMENT_PREPARE, exchange -> handle(
                    exchange, REPLACEMENT_PREPARE, root, Mode.PREPARE, jobs, replacement));
            server.createContext(REPLACEMENT_APPLY, exchange -> handle(
                    exchange, REPLACEMENT_APPLY, root, Mode.APPLY, jobs, replacement));
            server.createContext(REPLACEMENT_ROLLBACK, exchange -> handle(
                    exchange, REPLACEMENT_ROLLBACK, root, Mode.ROLLBACK, jobs, replacement));
            server.createContext(REPLACEMENT_CANCEL_PREPARATION, exchange -> handle(
                    exchange, REPLACEMENT_CANCEL_PREPARATION, root, Mode.CANCEL_PREPARATION, jobs, replacement));
            server.createContext(REPLACEMENT_FINISH, exchange -> handle(
                    exchange, REPLACEMENT_FINISH, root, Mode.FINISH, jobs, replacement));
        }
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(4), runnable -> {
                    Thread thread = new Thread(runnable, "nas-epub-agent");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        server.setExecutor(executor);
        server.start();
        return server;
    }

    private enum Mode { SINGLE, BATCH, JOB, PREPARE, APPLY, ROLLBACK, CANCEL_PREPARATION, FINISH }

    private static void handle(HttpExchange exchange, String endpoint, Path root, Mode mode, NasEpubJobStore jobs,
                               NasEpubSyntheticReplacement replacement)
            throws IOException {
        try (exchange) {
            if (!endpoint.equals(exchange.getRequestURI().getPath())
                    || exchange.getRequestURI().getRawQuery() != null) {
                reply(exchange, 404, "{\"error\":\"not_found\"}");
            } else if (!"POST".equals(exchange.getRequestMethod())) {
                reply(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            } else {
                byte[] bytes;
                try {
                    var result = new java.io.ByteArrayOutputStream();
                    switch (mode) {
                        case SINGLE -> NasEpubPrototype.run(exchange.getRequestBody(), result, root);
                        case BATCH -> NasEpubPrototype.runBatch(exchange.getRequestBody(), result, root);
                        case JOB -> jobs.run(exchange.getRequestBody(), result, root);
                        case PREPARE, APPLY, ROLLBACK, CANCEL_PREPARATION, FINISH ->
                                runReplacement(exchange.getRequestBody(), result, root, mode, replacement);
                    }
                    bytes = result.toByteArray();
                } catch (IllegalArgumentException e) {
                    log.warn("Rejected synthetic EPUB request", e);
                    if (e.getSuppressed().length > 0) {
                        reply(exchange, 500, "{\"error\":\"cleanup_failed\"}");
                    } else {
                        reply(exchange, 400, "{\"error\":\"invalid_request\"}");
                    }
                    return;
                } catch (IOException e) {
                    log.warn("Synthetic EPUB request failed", e);
                    if (e.getSuppressed().length > 0) {
                        reply(exchange, 500, "{\"error\":\"cleanup_failed\"}");
                    } else {
                        reply(exchange, 422, "{\"error\":\"processing_failed\"}");
                    }
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        }
    }

    private static void runReplacement(java.io.InputStream input, java.io.OutputStream output, Path root,
                                       Mode mode, NasEpubSyntheticReplacement replacement) throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var request = mapper.readValue(NasEpubPrototype.readRequest(input), NasEpubJobStore.JobRequest.class);
        NasEpubSyntheticReplacement.State state = switch (mode) {
            case PREPARE -> replacement.prepare(root, request);
            case APPLY -> replacement.apply(root, request);
            case ROLLBACK -> replacement.rollback(root, request);
            case CANCEL_PREPARATION -> replacement.cancelPreparation(root, request);
            case FINISH -> replacement.finish(root, request);
            default -> throw new IllegalArgumentException("Invalid synthetic replacement operation");
        };
        output.write(mapper.writeValueAsBytes(new NasEpubSyntheticReplacement.Result(request.operationId(), state)));
        output.write('\n');
    }

    static boolean syntheticReplacementEnabled(String value) {
        return booleanSetting("NAS_EPUB_AGENT_SYNTHETIC_REPLACEMENT", value);
    }

    private static boolean booleanSetting(String name, String value) {
        if (value == null || "false".equalsIgnoreCase(value)) {
            return false;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        throw new IllegalArgumentException("Invalid " + name + " value");
    }

    static SSLContext tlsContext(Path keyStorePath, char[] keyPassword,
                                 Path trustStorePath, char[] trustPassword)
            throws GeneralSecurityException, IOException {
        KeyStore keys = loadStore(keyStorePath, keyPassword);
        KeyStore trustedClients = loadStore(trustStorePath, trustPassword);
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keys, keyPassword);
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustedClients);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
        return tls;
    }

    private static KeyStore loadStore(Path path, char[] password) throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var stream = Files.newInputStream(path)) {
            store.load(stream, password);
        }
        return store;
    }

    private static char[] readPassword(Path path) throws IOException {
        byte[] bytes;
        try (var stream = Files.newInputStream(path)) {
            bytes = stream.readNBytes(1025);
        }
        if (bytes.length == 0 || bytes.length > 1024) {
            throw new IOException("Invalid secret file length");
        }
        String password = new String(bytes, StandardCharsets.UTF_8).stripTrailing();
        Arrays.fill(bytes, (byte) 0);
        if (password.isEmpty()) {
            throw new IOException("Empty secret file");
        }
        return password.toCharArray();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing environment variable " + name);
        }
        return value;
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
