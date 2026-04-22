package org.booklore.service.metadata.parser;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class DefaultJsoupConnectionFactory implements JsoupConnectionFactory {
    @Override
    public Connection connect(String url) {
        return Jsoup.connect(url);
    }
}
