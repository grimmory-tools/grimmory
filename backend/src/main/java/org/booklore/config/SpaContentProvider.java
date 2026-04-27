package org.booklore.config;

import jakarta.servlet.ServletContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Provides pre-processed SPA static content with the correct base path for subpath deployments.
 * Content is computed once at startup and reused for all requests.
 */
@Slf4j
@Component
public class SpaContentProvider {

    @Getter
    private final String basePath;

    @Getter
    private final boolean needsRewrite;

    private final String indexHtml;
    private final String manifest;
    private final String ngswJson;
    private final Resource indexHtmlResource;

    public SpaContentProvider(ServletContext servletContext) {
        // Normalize base path: ensure it ends with /
        String path = servletContext.getContextPath();
        if (path.isEmpty()) {
            path = "/";
        }
        if (!path.endsWith("/")) {
            path += "/";
        }
        this.basePath = path;
        this.needsRewrite = !"/".equals(basePath);

        if (needsRewrite) {
            this.indexHtml = loadAndRewrite("static/index.html", this::rewriteIndexHtml);
            this.manifest = loadAndRewrite("static/manifest.webmanifest", this::rewriteManifest);
            this.ngswJson = loadAndRewrite("static/ngsw.json", this::rewriteNgsw);

            // Pre-compute resource for WebMvcConfig SPA fallback
            if (indexHtml != null) {
                final long creationTime = System.currentTimeMillis();
                this.indexHtmlResource = new ByteArrayResource(indexHtml.getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public String getFilename() {
                        return "index.html";
                    }

                    @Override
                    public long lastModified() {
                        return creationTime;
                    }
                };
            } else {
                this.indexHtmlResource = new ClassPathResource("/static/index.html");
            }

            log.info("Base path configured: {}", basePath);
        } else {
            this.indexHtml = null;
            this.manifest = null;
            this.ngswJson = null;
            this.indexHtmlResource = new ClassPathResource("/static/index.html");
        }
    }

    /**
     * Returns the rewritten index.html content, or null if no rewrite is needed.
     */
    public String getIndexHtml() {
        return indexHtml;
    }

    /**
     * Returns the rewritten manifest.webmanifest content, or null if no rewrite is needed.
     */
    public String getManifest() {
        return manifest;
    }

    /**
     * Returns the rewritten ngsw.json content, or null if no rewrite is needed.
     */
    public String getNgswJson() {
        return ngswJson;
    }

    /**
     * Returns a Resource for the index.html (rewritten if needed) for SPA fallback.
     */
    public Resource getIndexHtmlResource() {
        return indexHtmlResource;
    }

    private String loadAndRewrite(String resourcePath, java.util.function.Function<String, String> rewriter) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (resource.exists()) {
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                return rewriter.apply(content);
            }
        } catch (IOException e) {
            log.warn("Failed to load {} for base path rewriting: {}", resourcePath, e.getMessage());
        }
        return null;
    }

    private String rewriteIndexHtml(String content) {
        return content.replace("<base href=\"/\">", "<base href=\"" + basePath + "\">");
    }

    private String rewriteManifest(String content) {
        return content
                .replace("\"start_url\": \"./\"", "\"start_url\": \"" + basePath + "\"")
                .replace("\"scope\": \"./\"", "\"scope\": \"" + basePath + "\"");
    }

    private String rewriteNgsw(String content) {
        String baseWithoutSlash = basePath.substring(0, basePath.length() - 1);
        return content.replaceAll("\"(/[^\"]+)\"", "\"" + baseWithoutSlash + "$1\"");
    }
}
