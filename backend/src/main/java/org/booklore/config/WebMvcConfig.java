package org.booklore.config;

import lombok.RequiredArgsConstructor;
import org.booklore.interceptor.KomgaCleanInterceptor;
import org.booklore.interceptor.KomgaEnabledInterceptor;
import org.booklore.interceptor.OpdsEnabledInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Cache-Control for fingerprinted Angular build output. These filenames embed a
     * content hash (e.g. {@code main-X2KZKPZ6.js}, {@code styles-ZIJULZVY.css}, and
     * the {@code /media/} assets bundled by @angular/build) so any content change
     * produces a new filename, which makes a 1-year immutable cache safe.
     */
    private static final CacheControl IMMUTABLE_ONE_YEAR = CacheControl
            .maxAge(Duration.ofDays(365))
            .cachePublic()
            .immutable();

    /**
     * Cache-Control for unhashed resources that must be revalidated so the client
     * always picks up the latest deploy (index.html drives chunk discovery, the
     * service worker files must revalidate to publish app updates).
     */
    private static final CacheControl NO_CACHE_REVALIDATE = CacheControl
            .noCache()
            .cachePublic();

    /**
     * Cache-Control for third-party frontend assets that are not fingerprinted but
     * change infrequently (PWA icons, bundled reader libraries in /assets/**).
     * A 1-day public cache balances freshness with the perceived-performance win.
     */
    private static final CacheControl ONE_DAY_PUBLIC = CacheControl
            .maxAge(Duration.ofDays(1))
            .cachePublic();

    private final OpdsEnabledInterceptor opdsEnabledInterceptor;
    private final KomgaEnabledInterceptor komgaEnabledInterceptor;
    private final KomgaCleanInterceptor komgaCleanInterceptor;

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(new VirtualThreadTaskExecutor("mvc-async-"));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Service worker control files must revalidate so app updates can be picked up.
        // (More specific than /*.js so it wins the pattern match regardless of
        // registration order, but we register it first for clarity.)
        registry.addResourceHandler(
                        "/ngsw-worker.js",
                        "/ngsw.json",
                        "/safety-worker.js",
                        "/worker-basic.min.js")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(NO_CACHE_REVALIDATE);

        // Fingerprinted Angular bundles at the root of the dist output.
        registry.addResourceHandler("/*.js", "/*.css")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(IMMUTABLE_ONE_YEAR);

        // Fingerprinted media assets emitted by @angular/build (fonts, inlined images).
        registry.addResourceHandler("/media/**")
                .addResourceLocations("classpath:/static/media/")
                .setCacheControl(IMMUTABLE_ONE_YEAR);

        // Non-fingerprinted but rarely-changing third-party assets (foliate, pdfium,
        // embedpdf, PWA icons, etc.).
        registry.addResourceHandler("/assets/**", "/icons/**", "/manifest.webmanifest", "/favicon.*")
                .addResourceLocations(
                        "classpath:/static/assets/",
                        "classpath:/static/icons/",
                        "classpath:/static/")
                .setCacheControl(ONE_DAY_PUBLIC);

        // SPA fallback: index.html must always be revalidated so a fresh deploy
        // surfaces new hashed chunk URLs immediately.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(NO_CACHE_REVALIDATE)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = location.createRelative(resourcePath);
                        if (resource.exists() && resource.isReadable()) {
                            return resource;
                        }

                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() && index.isReadable() ? index : null;
                    }
                });
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(opdsEnabledInterceptor)
                .addPathPatterns("/api/v1/opds/**", "/api/v2/opds/**");
        registry.addInterceptor(komgaEnabledInterceptor)
                .addPathPatterns("/komga/api/**");
        registry.addInterceptor(komgaCleanInterceptor)
                .addPathPatterns("/komga/api/**");
    }
}
