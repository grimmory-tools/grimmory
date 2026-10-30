package org.booklore.config;

import jakarta.servlet.ServletContext;
import lombok.Getter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prepares the SPA entry point and its service-worker metadata once per deployment. */
@Getter
@Component
public class SpaContentProvider {

    private static final Pattern BASE_HREF = Pattern.compile(
            "(<base\\b[^>]*?\\s+href\\s*=\\s*)([\"'])(.*?)\\2", Pattern.CASE_INSENSITIVE);

    private final Resource indexHtmlResource;
    private final Resource manifestResource;
    private final Resource ngswResource;

    public SpaContentProvider(ServletContext servletContext, ResourceLoader resourceLoader) {
        String contextPath = servletContext.getContextPath();
        Resource index = resourceLoader.getResource("classpath:/static/index.html");
        Resource ngsw = resourceLoader.getResource("classpath:/static/ngsw.json");
        // scope, start_url and icons are relative to the manifest URL, so no rewrite is needed.
        manifestResource = resourceLoader.getResource("classpath:/static/manifest.webmanifest");
        try {
            indexHtmlResource = !contextPath.isEmpty() && index.exists()
                    ? cachedResource("index.html", rewriteIndex(index.getContentAsString(StandardCharsets.UTF_8), contextPath))
                    : index;
            ngswResource = !contextPath.isEmpty() && ngsw.exists()
                    ? cachedResource("ngsw.json", rewriteNgsw(ngsw.getContentAsString(StandardCharsets.UTF_8), contextPath))
                    : ngsw;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare SPA resources", e);
        }
    }

    private String rewriteIndex(String html, String prefix) {
        Matcher matcher = BASE_HREF.matcher(html);
        if (matcher.results().count() != 1 || !matcher.reset().find() || !matcher.group(3).equals("/")) {
            throw new IllegalStateException("SPA index must contain exactly one root base href for runtime BASE_PATH rewriting");
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(
                matcher.group(1) + matcher.group(2) + HtmlUtils.htmlEscape(prefix + "/") + matcher.group(2)));
    }

    private String rewriteNgsw(String content, String prefix) throws IOException {
        ObjectMapper mapper = JsonMapper.shared();
        ObjectNode manifest = (ObjectNode) mapper.readTree(content);
        manifest.put("index", prefix + manifest.path("index").asText());
        ObjectNode hashes = (ObjectNode) manifest.get("hashTable");
        if (hashes != null) {
            ObjectNode rewritten = mapper.createObjectNode();
            hashes.properties().forEach(entry -> rewritten.set(prefix + entry.getKey(), entry.getValue()));
            if (indexHtmlResource.exists() && hashes.has("/index.html")) {
                rewritten.put(prefix + "/index.html", sha1(indexHtmlResource.getContentAsByteArray()));
            }
            manifest.set("hashTable", rewritten);
        }
        for (JsonNode group : manifest.path("assetGroups")) {
            ArrayNode urls = (ArrayNode) group.get("urls");
            if (urls != null) {
                for (int i = 0; i < urls.size(); i++) {
                    String url = urls.get(i).asText();
                    if (url.startsWith("/") && !url.startsWith("//")) {
                        urls.set(i, mapper.getNodeFactory().stringNode(prefix + url));
                    }
                }
            }
            prefixPatterns(group, prefix, mapper);
        }
        for (JsonNode group : manifest.path("dataGroups")) {
            prefixPatterns(group, prefix, mapper);
        }
        for (JsonNode navigation : manifest.path("navigationUrls")) {
            ((ObjectNode) navigation).put("regex", prefixPattern(navigation.path("regex").asText(), prefix));
        }
        return mapper.writeValueAsString(manifest);
    }

    private void prefixPatterns(JsonNode group, String prefix, ObjectMapper mapper) {
        ArrayNode patterns = (ArrayNode) group.get("patterns");
        if (patterns != null) {
            for (int i = 0; i < patterns.size(); i++) {
                patterns.set(i, mapper.getNodeFactory().stringNode(prefixPattern(patterns.get(i).asText(), prefix)));
            }
        }
    }

    private String prefixPattern(String regex, String prefix) {
        String anchor = regex.startsWith("^") ? "^" : "";
        String pattern = regex.substring(anchor.length());
        if (!pattern.startsWith("/") && !pattern.startsWith("\\/")) return regex;
        // Escape for JavaScript RegExp, not Java's \Q...\E syntax.
        String escapedPrefix = prefix.replaceAll("([.\\\\+*?\\[\\]^$(){}|])", "\\\\$1");
        return anchor + escapedPrefix + pattern;
    }

    private String sha1(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Resource cachedResource(String filename, String content) {
        long lastModified = System.currentTimeMillis();
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public long lastModified() {
                return lastModified;
            }
        };
    }
}
