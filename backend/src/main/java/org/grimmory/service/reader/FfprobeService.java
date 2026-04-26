package org.grimmory.service.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.util.FileService;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Slf4j
@Service
@AllArgsConstructor
public class FfprobeService {

    private final FileService fileService;

    public Path getFfprobeBinary() {
        return fileService.findSystemFile("ffprobe");
    }
}
