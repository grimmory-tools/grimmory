package org.booklore.service.user;

import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.settings.UserSettingKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DefaultUserSettingsProviderTest {

    private DefaultUserSettingsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DefaultUserSettingsProvider();
        provider.init();
    }

    @Test
    void ebookReaderDefaults_shouldEnableTapToTurnPage() {
        Object value = provider.getDefaultValue(UserSettingKey.EBOOK_READER_SETTING);

        BookLoreUser.UserSettings.EbookReaderSetting setting =
                assertInstanceOf(BookLoreUser.UserSettings.EbookReaderSetting.class, value);
        assertEquals(Boolean.TRUE, setting.getTapToTurnPage());
    }
}
