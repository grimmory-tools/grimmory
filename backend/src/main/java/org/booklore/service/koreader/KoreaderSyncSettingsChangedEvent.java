package org.booklore.service.koreader;

import org.booklore.model.dto.settings.KoreaderSyncSettings;

public record KoreaderSyncSettingsChangedEvent(KoreaderSyncSettings before, KoreaderSyncSettings after) {
}
