package org.booklore.mapper;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookMetadataEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class BookMetadataMapperTest {

    @Mock
    private AuthorMapper authorMapper;
    
    @Mock
    private CategoryMapper categoryMapper;
    
    @Mock
    private MoodMapper moodMapper;
    
    @Mock
    private TagMapper tagMapper;
    
    @Mock
    private ComicMetadataMapper comicMetadataMapper;

    private BookMetadataMapperImpl bookMetadataMapper;

    @BeforeEach
    void setUp() {
        bookMetadataMapper = new BookMetadataMapperImpl();
        ReflectionTestUtils.setField(bookMetadataMapper, "authorMapper", authorMapper);
        ReflectionTestUtils.setField(bookMetadataMapper, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(bookMetadataMapper, "moodMapper", moodMapper);
        ReflectionTestUtils.setField(bookMetadataMapper, "tagMapper", tagMapper);
        ReflectionTestUtils.setField(bookMetadataMapper, "comicMetadataMapper", comicMetadataMapper);
    }

    @Test
    void testMapping() {
        BookMetadataEntity entity = new BookMetadataEntity();
        entity.setTitle("Test Title");
        entity.setHardcoverId("hc-id");
        entity.setHardcoverRating(4.5);
        entity.setGoodreadsId("gr-id");
        entity.setAuthors(new ArrayList<>());
        entity.setCategories(new HashSet<>());
        entity.setMoods(new HashSet<>());
        entity.setTags(new HashSet<>());

        BookMetadata dto = bookMetadataMapper.toBookMetadata(entity);

        assertEquals("Test Title", dto.getTitle());
        assertEquals("hc-id", dto.getHardcoverId());
        assertEquals(4.5, dto.getHardcoverRating());
        assertEquals("gr-id", dto.getGoodreadsId());
    }
}

