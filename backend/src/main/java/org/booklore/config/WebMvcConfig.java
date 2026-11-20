package org.booklore.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.booklore.interceptor.KomgaCleanInterceptor;
import org.booklore.interceptor.KomgaEnabledInterceptor;
import org.booklore.interceptor.OpdsEnabledInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.util.List;
import java.util.Set;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    // Keep aligned with app.routes.ts. Static assets and APIs must never receive SPA HTML.
    private static final Set<String> SPA_ROUTES = Set.of(
            "setup", "oauth2-callback", "dashboard", "all-books", "settings", "library", "shelf",
            "unshelved-books", "series", "authors", "author", "magic-shelf", "book", "bookdrop",
            "metadata-manager", "library-stats", "reading-stats", "notebook", "design-system",
            "pdf-reader", "ebook-reader", "cbx-reader", "audiobook-player", "login", "change-password");

    private final OpdsEnabledInterceptor opdsEnabledInterceptor;
    private final KomgaEnabledInterceptor komgaEnabledInterceptor;
    private final KomgaCleanInterceptor komgaCleanInterceptor;
    private final SpaContentProvider spaContentProvider;

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(new VirtualThreadTaskExecutor("mvc-async-"));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        final Resource spaFallback = spaContentProvider.getIndexHtmlResource();

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noStore().noTransform())
                // Navigation fallback depends on Accept; do not cache resolution by path alone.
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource resolveResourceInternal(HttpServletRequest request, String resourcePath,
                            List<? extends Resource> locations, ResourceResolverChain chain) {
                        Resource resource = super.resolveResourceInternal(request, resourcePath, locations, chain);
                        if (resource != null) {
                            return resource;
                        }
                        String route = resourcePath.split("/", 2)[0];
                        if (request == null || !SPA_ROUTES.contains(route)
                                || !(request.getMethod().equals("GET") || request.getMethod().equals("HEAD"))
                                || request.getHeader("Accept") == null
                                || MediaType.parseMediaTypes(request.getHeader("Accept")).stream()
                                        .noneMatch(type -> type.isConcrete() && type.isCompatibleWith(MediaType.TEXT_HTML))) {
                            return null;
                        }
                        return spaFallback.exists() && spaFallback.isReadable() ? spaFallback : null;
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
