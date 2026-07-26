package org.booklore.service.kobo;

import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
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
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    @Test
    void convertEpubToKepub_ShouldOnlyTransformHTML() throws IOException {
        Book book = new Book();
        book.addResource(new Resource("html", "html".getBytes(StandardCharsets.UTF_8), "/example.html", MediaTypes.XHTML));
        book.addResource(new Resource("jpg", "jpg".getBytes(StandardCharsets.UTF_8), "/example.jpg", MediaTypes.JPG));
        book.addResource(new Resource("xhtml", "xhtml".getBytes(StandardCharsets.UTF_8), "/example.xhtml", MediaTypes.XHTML));
        book.addResource(new Resource("txt", "txt".getBytes(StandardCharsets.UTF_8), "/example.txt", MediaTypes.getMediaTypeByName("text/plain")));

        when(epubReader.readEpub(any(InputStream.class))).thenReturn(book);
        when(kepubHtmlConversionService.transform(any(), eq("UTF-8"), eq(true))).then(
                args -> {
                    byte[] bytes = ((ByteArrayInputStream) args.getArguments()[0]).readAllBytes();
                    return "transformed " + new String(bytes, StandardCharsets.UTF_8);
                }
        );

        kepubConversionService.convertEpubToKepub(
                InputStream.nullInputStream(),
                OutputStream.nullOutputStream(),
                true
        );

        ArgumentCaptor<ByteArrayInputStream> actualHtml = ArgumentCaptor.forClass(ByteArrayInputStream.class);
        verify(kepubHtmlConversionService, times(2)).transform(actualHtml.capture(), eq("UTF-8"), eq(true));

        ArgumentCaptor<Book> actualBook = ArgumentCaptor.forClass(Book.class);
        verify(epubWriter).write(actualBook.capture(), any());

        var hrefs = actualBook.getValue()
                .getResources()
                .getAll()
                .stream()
                .map(r -> {
                    try {
                        return r.getData();
                    } catch(Exception e) {
                        return null;
                    }
                })
                .map(d -> new String(d, StandardCharsets.UTF_8))
                .toList();

        assertThat(hrefs).hasSize(4);
        assertThat(hrefs).contains("jpg");
        assertThat(hrefs).contains("txt");
        assertThat(hrefs).contains("transformed html");
        assertThat(hrefs).contains("transformed xhtml");
    }
}