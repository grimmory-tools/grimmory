package org.grimmory.model.websocket;

import java.util.Map;

public record LibraryHealthPayload(Map<Long, Boolean> libraryHealth) {}
