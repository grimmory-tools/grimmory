package org.booklore.service.kobo;

import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.EpubReader;
import org.grimmory.epub4j.epub.EpubWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;
import java.io.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KepubConversionServiceTest {
    @Mock
    private EpubReader epubReader;

    @Mock
    private EpubWriter epubWriter;

    @Mock
    private KepubHtmlConversionService kepubHtmlConversionService;

    @InjectMocks
    private KepubConversionService kepubConversionService;

    @Test
    void convertEpubToKepub_WithValidEpub_ShouldConvert() throws IOException {
        Book book = new Book();

        when(epubReader.readEpub(any(InputStream.class))).thenReturn(book);

        kepubConversionService.convertEpubToKepub(
                InputStream.nullInputStream(),
                OutputStream.nullOutputStream(),
                true
        );
    }

    @Test
    void convertEpubToKepub_ShouldSkipSomeFiles() throws IOException {
        Book book = new Book();
        book.addResource(new Resource("/example/foo.txt"));
        book.addResource(new Resource("/example/.DS_STORE"));
        book.addResource(new Resource("/__MACOSX/bar.txt"));
        book.addResource(new Resource("/other.txt"));

        when(epubReader.readEpub(any(InputStream.class))).thenReturn(book);

        kepubConversionService.convertEpubToKepub(
                InputStream.nullInputStream(),
                OutputStream.nullOutputStream(),
                true
        );

        ArgumentCaptor<Book> actualBook = ArgumentCaptor.forClass(Book.class);
        verify(epubWriter).write(actualBook.capture(), any());

        var hrefs = actualBook.getValue()
                .getResources()
                .getAll()
                .stream()
                .map(Resource::getHref)
                .toList();

        assertThat(hrefs).hasSize(2);
        assertThat(hrefs).contains("/example/foo.txt");
        assertThat(hrefs).contains("/other.txt");
    }
}