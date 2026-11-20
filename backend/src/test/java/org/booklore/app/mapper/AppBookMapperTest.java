package org.booklore.app.mapper;

import org.booklore.model.entity.BookEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class AppBookMapperTest {
    @AfterEach
    void resetRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/grimmory", "/apps/books"})
    void thumbnailUsesExistingMediaEndpointUnderContextPath(String prefix) {
        var request = new MockHttpServletRequest();
        request.setContextPath(prefix);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var book = new BookEntity();
        book.setId(42L);
        assertThat(Mappers.getMapper(AppBookMapper.class).mapThumbnailUrl(book))
                .isEqualTo(prefix + "/api/v1/media/book/42/cover");
    }
}
