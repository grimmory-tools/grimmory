package org.booklore.service.metadata.writer;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class NasEpubAgentClient implements NasEpubSyntheticReplacementCoordinator.Agent {
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private final URI endpoint;
    private final HttpClient client;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public NasEpubAgentClient(URI endpoint, SSLContext tls, Duration timeout) {
        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(tls);
        Objects.requireNonNull(timeout);
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !"/v1/synthetic-epub".equals(endpoint.getPath())
                || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Invalid agent endpoint or timeout");
        }
        this.endpoint = endpoint;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().sslContext(tls).connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public NasEpubPrototype.Result write(NasEpubPrototype.Request request) throws IOException, InterruptedException {
        Objects.requireNonNull(request);
        NasEpubPrototype.Result result = send(endpoint, request, NasEpubPrototype.Result.class);
        validateResult(request, result);
        return result;
    }

    public NasEpubPrototype.BatchResult writeBatch(NasEpubPrototype.BatchRequest batch)
            throws IOException, InterruptedException {
        Objects.requireNonNull(batch);
        NasEpubPrototype.BatchResult result = send(endpoint.resolve("/v1/synthetic-batch"),
                batch, NasEpubPrototype.BatchResult.class);
        validateBatch(batch, result);
        return result;
    }

    public NasEpubJobStore.JobResult runJob(NasEpubJobStore.JobRequest request)
            throws IOException, InterruptedException {
        Objects.requireNonNull(request);
        NasEpubJobStore.JobResult result = send(endpoint.resolve("/v1/synthetic-job"),
                request, NasEpubJobStore.JobResult.class);
        if (request.operationId() == null || result == null
                || !request.operationId().equals(result.operationId())) {
            throw new IOException("Invalid agent job response");
        }
        validateBatch(request.batch(), result.batch());
        return result;
    }

    @Override
    public NasEpubSyntheticReplacement.Result prepareReplacement(NasEpubJobStore.JobRequest request)
            throws IOException, InterruptedException {
        return replacement("/v1/synthetic-replacement/prepare", request, null);
    }

    @Override
    public NasEpubSyntheticReplacement.Result applyReplacement(NasEpubJobStore.JobRequest request)
            throws IOException, InterruptedException {
        return replacement("/v1/synthetic-replacement/apply", request, NasEpubSyntheticReplacement.State.APPLIED);
    }

    @Override
    public NasEpubSyntheticReplacement.Result rollbackReplacement(NasEpubJobStore.JobRequest request)
            throws IOException, InterruptedException {
        return replacement("/v1/synthetic-replacement/rollback", request, NasEpubSyntheticReplacement.State.ROLLED_BACK);
    }

    @Override
    public NasEpubSyntheticReplacement.Result cancelPreparation(NasEpubJobStore.JobRequest request)
            throws IOException, InterruptedException {
        return replacement("/v1/synthetic-replacement/cancel-preparation", request, NasEpubSyntheticReplacement.State.ROLLED_BACK);
    }

    @Override
    public NasEpubSyntheticReplacement.Result finishReplacement(NasEpubJobStore.JobRequest request)
            throws IOException, InterruptedException {
        return replacement("/v1/synthetic-replacement/finish", request, NasEpubSyntheticReplacement.State.COMMITTED);
    }

    private NasEpubSyntheticReplacement.Result replacement(String path, NasEpubJobStore.JobRequest request,
                                                            NasEpubSyntheticReplacement.State expected)
            throws IOException, InterruptedException {
        Objects.requireNonNull(request);
        if (request.operationId() == null) {
            throw new IllegalArgumentException("An operation ID is required");
        }
        var result = send(endpoint.resolve(path), request, NasEpubSyntheticReplacement.Result.class);
        if (result == null || !request.operationId().equals(result.operationId()) || result.state() == null
                || (expected != null && result.state() != expected)) {
            throw new IOException("Invalid agent replacement response");
        }
        return result;
    }

    private <T> T send(URI uri, Object request, Class<T> responseType) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(request))).build();
        HttpResponse<byte[]> response = client.send(httpRequest,
                HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), MAX_RESPONSE_BYTES));
        if (response.statusCode() != 200) {
            throw new IOException("Agent rejected or failed the request (HTTP " + response.statusCode() + ")");
        }
        return mapper.readValue(response.body(), responseType);
    }

    private void validateResult(NasEpubPrototype.Request request, NasEpubPrototype.Result result) throws IOException {
        if (request == null || result == null || !Objects.equals(request.output(), result.output())
                || result.outputSha256() == null || !result.outputSha256().matches("[a-f0-9]{64}")) {
            throw new IOException("Invalid agent response");
        }
    }

    private void validateBatch(NasEpubPrototype.BatchRequest batch, NasEpubPrototype.BatchResult result) throws IOException {
        if (batch == null || result == null || result.results() == null || batch.requests() == null
                || result.results().size() != batch.requests().size()) {
            throw new IOException("Invalid agent batch response");
        }
        for (int i = 0; i < result.results().size(); i++) {
            validateResult(batch.requests().get(i), result.results().get(i));
        }
    }
}
