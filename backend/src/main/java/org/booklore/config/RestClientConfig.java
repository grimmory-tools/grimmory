package org.booklore.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.InetAddressFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class RestClientConfig {
    private final AppProperties appProperties;

    @Bean
    public InetAddressFilter outboundInetAddressFilter() {
        var filter = InetAddressFilter
                .not(InetAddressFilter.multicast());

        var restrictedRanges = appProperties.getOutbound().getRestrictedRanges();

        if (!restrictedRanges.isEmpty()) {
            filter = filter.andNot(restrictedRanges.toArray(new String[]{}));
        }

        return filter;
    }

    @Bean
    @Primary
    public ClientHttpRequestFactory clientHttpRequestFactory(InetAddressFilter outboundInetAddressFilter) {
        var outbound = appProperties.getOutbound();
        int connectTimeout = outbound.getConnectTimeout();
        int readTimeout = outbound.getReadTimeout();

        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(connectTimeout))
                .withReadTimeout(Duration.ofSeconds(readTimeout))
                .withInetAddressFilter(outboundInetAddressFilter);

        return ClientHttpRequestFactoryBuilder
                        .jdk()
                        .build(settings);
    }

    @Bean
    @Qualifier("unsafe")
    public ClientHttpRequestFactory unsafeClientHttpRequestFactory() {
        var outbound = appProperties.getOutbound();
        int connectTimeout = outbound.getConnectTimeout();
        int readTimeout = outbound.getReadTimeout();

        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(connectTimeout))
                .withReadTimeout(Duration.ofSeconds(readTimeout))
                .withInetAddressFilter(InetAddressFilter.all());

        return ClientHttpRequestFactoryBuilder
                .jdk()
                .build(settings);
    }

    @Bean
    public RestClient restClient(ClientHttpRequestFactory clientHttpRequestFactory) {
        return RestClient.builder()
                .requestFactory(clientHttpRequestFactory)
                .build();
    }

    @Bean
    @Primary
    public RestTemplate restTemplate(ClientHttpRequestFactory clientHttpRequestFactory) {
        return new RestTemplate(clientHttpRequestFactory);
    }

    @Bean
    @Qualifier("oidc")
    public RestTemplate oidcRestTemplate(
            ClientHttpRequestFactory clientHttpRequestFactory,
            @Qualifier("unsafe")
            ClientHttpRequestFactory unsafeClientHttpRequestFactory
    ) {
        var isAllowingUnsafeHosts = appProperties.getOidc().getAllowUnsafeHosts();

        if (isAllowingUnsafeHosts) {
            return new RestTemplate(unsafeClientHttpRequestFactory);
        }

        return new RestTemplate(clientHttpRequestFactory);
    }
}
