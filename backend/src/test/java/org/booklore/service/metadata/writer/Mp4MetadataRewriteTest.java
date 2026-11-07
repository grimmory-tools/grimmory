package org.booklore.service.metadata.writer;

import org.jaudiotagger.audio.mp4.Flatten;
import org.jaudiotagger.audio.mp4.ReplaceMP4Editor;
import org.jcodec.containers.mp4.MP4Util;
import org.jcodec.containers.mp4.boxes.FileTypeBox;
import org.jcodec.containers.mp4.boxes.HandlerBox;
import org.jcodec.containers.mp4.boxes.MediaBox;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.boxes.TrakBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

class Mp4MetadataRewriteTest {
    @TempDir
    Path tempDir;

    @Test
    void growingMetadataPreservesAudioAndChapterTracksInFallbackRewrite() throws Exception {
        MovieBox moov = MovieBox.createMovieBox();
        moov.add(track("soun"));
        moov.add(track("text"));
        var movie = new MP4Util.Movie(
                FileTypeBox.createFileTypeBox("isom", 0, List.of("isom")), moov, List.of());
        Path source = tempDir.resolve("audiobook.m4b");
        try (FileChannel channel = FileChannel.open(source, CREATE_NEW, WRITE)) {
            MP4Util.writeFullMovie(channel, movie);
        }

        // Exercise the fallback used when a larger tag/cover cannot fit in the old moov.
        // Only sample copying is mocked: the dependency parses and edits the real boxes.
        try (var flatten = mockConstruction(Flatten.class);
             FileChannel input = FileChannel.open(source, READ);
             FileChannel output = FileChannel.open(tempDir.resolve("updated.m4b"), CREATE_NEW, WRITE)) {
            new ReplaceMP4Editor().copy(input, output, moov);

            var rewritten = ArgumentCaptor.forClass(MP4Util.Movie.class);
            verify(flatten.constructed().getFirst()).flattenChannel(rewritten.capture(), same(output));
            assertThat(rewritten.getValue().getMoov().getTracks())
                    .extracting(TrakBox::getHandlerType)
                    .containsExactly("soun", "text");
        }
    }

    private TrakBox track(String handler) {
        TrakBox track = TrakBox.createTrakBox();
        MediaBox media = MediaBox.createMediaBox();
        media.add(HandlerBox.createHandlerBox("mhlr", handler, "appl", 0, 0));
        track.add(media);
        return track;
    }
}
