package org.booklore.service.metadata.parser;

import org.jsoup.Connection;

public interface JsoupConnectionFactory {
    Connection connect(String url);
}
