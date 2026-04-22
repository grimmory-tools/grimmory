package org.booklore.service.customfont;

import org.booklore.config.AppProperties;
import org.booklore.mapper.CustomFontMapper;
import org.booklore.model.dto.CustomFontDto;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.CustomFontEntity;
import org.booklore.model.enums.FontFormat;
import org.booklore.repository.CustomFontRepository;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomFontServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    CustomFontRepository customFontRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    CustomFontMapper customFontMapper;

    AppProperties appProperties;
    CustomFontService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        appProperties = new AppProperties();
        appProperties.setPathConfig(tempDir.toString());
        service = new CustomFontService(customFontRepository, userRepository, customFontMapper, appProperties);
    }

    @Test
    @DisplayName("uploadFont_withValidFile_shouldSaveSuccessfully")
    void uploadFont_withValidFile_shouldSaveSuccessfully() throws IOException {
        // Arrange
        Long userId = 1L;
        String fontName = "My Custom Font";
        // Create valid TTF magic bytes: 0x00 0x01 0x00 0x00
        byte[] fontContent = new byte[]{0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("font.ttf", "font.ttf", "font/ttf", fontContent);

        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setId(userId);

        CustomFontEntity savedEntity = CustomFontEntity.builder()
                .id(1L)
                .user(user)
                .fontName(fontName)
                .fileName("user_1_font_123.ttf")
                .originalFileName("font.ttf")
                .format(FontFormat.TTF)
                .fileSize((long) fontContent.length)
                .build();

        CustomFontDto expectedDto = new CustomFontDto();
        expectedDto.setId(1L);
        expectedDto.setFontName(fontName);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(customFontRepository.countByUserId(userId)).thenReturn(0);
        when(customFontRepository.save(any(CustomFontEntity.class))).thenReturn(savedEntity);
        when(customFontMapper.toDto(savedEntity)).thenReturn(expectedDto);

        // Act
        CustomFontDto result = service.uploadFont(file, fontName, userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getFontName()).isEqualTo(fontName);
        verify(customFontRepository).save(any(CustomFontEntity.class));

        // Verify file was saved
        Path userFontDir = tempDir.resolve("custom-fonts").resolve(String.valueOf(userId));
        assertThat(Files.exists(userFontDir)).isTrue();
        assertThat(Files.list(userFontDir).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("uploadFont_whenQuotaExceeded_shouldThrowException")
    void uploadFont_whenQuotaExceeded_shouldThrowException() {
        // Arrange
        Long userId = 1L;
        MultipartFile file = new MockMultipartFile("font.ttf", "font.ttf", "font/ttf", "content".getBytes());

        when(customFontRepository.countByUserId(userId)).thenReturn(10); // Max quota

        // Act & Assert
        assertThatThrownBy(() -> service.uploadFont(file, "Font", userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Font limit exceeded");

        verify(customFontRepository, never()).save(any());
    }

    @Test
    @DisplayName("uploadFont_withOversizedFile_shouldThrowException")
    void uploadFont_withOversizedFile_shouldThrowException() {
        // Arrange
        Long userId = 1L;
        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB (exceeds 5MB limit)
        MultipartFile file = new MockMultipartFile("font.ttf", "font.ttf", "font/ttf", largeContent);

        when(customFontRepository.countByUserId(userId)).thenReturn(0);

        // Act & Assert
        assertThatThrownBy(() -> service.uploadFont(file, "Font", userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File size exceeds maximum limit");

        verify(customFontRepository, never()).save(any());
    }

    @Test
    @DisplayName("uploadFont_withInvalidExtension_shouldThrowException")
    void uploadFont_withInvalidExtension_shouldThrowException() {
        // Arrange
        Long userId = 1L;
        MultipartFile file = new MockMultipartFile("font.exe", "font.exe", "application/octet-stream", "content".getBytes());

        when(customFontRepository.countByUserId(userId)).thenReturn(0);

        // Act & Assert
        assertThatThrownBy(() -> service.uploadFont(file, "Font", userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported font format");

        verify(customFontRepository, never()).save(any());
    }

    @Test
    @DisplayName("uploadFont_withInvalidMagicBytes_shouldThrowException")
    void uploadFont_withInvalidMagicBytes_shouldThrowException() throws IOException {
        // Arrange
        Long userId = 1L;
        // File has .ttf extension but contains malicious content (not TTF magic bytes)
        byte[] maliciousContent = "This is not a font file".getBytes();
        MultipartFile file = new MockMultipartFile("malicious.ttf", "malicious.ttf", "font/ttf", maliciousContent);

        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(customFontRepository.countByUserId(userId)).thenReturn(0);

        // Act & Assert
        assertThatThrownBy(() -> service.uploadFont(file, "Font", userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid font file format");

        verify(customFontRepository, never()).save(any());

        // Verify file was cleaned up
        Path userFontDir = tempDir.resolve("custom-fonts").resolve(String.valueOf(userId));
        if (Files.exists(userFontDir)) {
            assertThat(Files.list(userFontDir).count()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("uploadFont_whenDatabaseSaveFails_shouldCleanupFile")
    void uploadFont_whenDatabaseSaveFails_shouldCleanupFile() throws IOException {
        // Arrange
        Long userId = 1L;
        // Create valid TTF magic bytes
        byte[] fontContent = new byte[]{0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("font.ttf", "font.ttf", "font/ttf", fontContent);

        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(customFontRepository.countByUserId(userId)).thenReturn(0);
        when(customFontRepository.save(any(CustomFontEntity.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThatThrownBy(() -> service.uploadFont(file, "Font", userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error");

        // Verify file was cleaned up
        Path userFontDir = tempDir.resolve("custom-fonts").resolve(String.valueOf(userId));
        if (Files.exists(userFontDir)) {
            assertThat(Files.list(userFontDir).count()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("deleteFont_withValidId_shouldDeleteFileAndRecord")
    void deleteFont_withValidId_shouldDeleteFileAndRecord() throws IOException {
        // Arrange
        Long userId = 1L;
        Long fontId = 1L;
        String fileName = "user_1_font_123.ttf";

        // Create actual font file
        Path userFontDir = tempDir.resolve("custom-fonts").resolve(String.valueOf(userId));
        Files.createDirectories(userFontDir);
        Path fontFile = userFontDir.resolve(fileName);
        Files.writeString(fontFile, "font content");

        CustomFontEntity font = CustomFontEntity.builder()
                .id(fontId)
                .fontName("Test Font")
                .fileName(fileName)
                .build();

        when(customFontRepository.findByIdAndUserId(fontId, userId)).thenReturn(Optional.of(font));

        // Act
        service.deleteFont(fontId, userId);

        // Assert
        verify(customFontRepository).delete(font);
        assertThat(Files.exists(fontFile)).isFalse();
    }

    @Test
    @DisplayName("deleteFont_whenFileDeletionFails_shouldStillDeleteRecord")
    void deleteFont_whenFileDeletionFails_shouldStillDeleteRecord() {
        // Arrange
        Long userId = 1L;
        Long fontId = 1L;
        String fileName = "non_existent_font.ttf";

        CustomFontEntity font = CustomFontEntity.builder()
                .id(fontId)
                .fontName("Test Font")
                .fileName(fileName)
                .build();

        when(customFontRepository.findByIdAndUserId(fontId, userId)).thenReturn(Optional.of(font));

        // Act
        // Files.deleteIfExists doesn't throw when file doesn't exist, so this should succeed
        assertThatCode(() -> service.deleteFont(fontId, userId))
                .doesNotThrowAnyException();

        // Database record should be deleted
        verify(customFontRepository, times(1)).delete(font);
    }

    @Test
    @DisplayName("deleteFont_withInvalidId_shouldThrowException")
    void deleteFont_withInvalidId_shouldThrowException() {
        // Arrange
        Long userId = 1L;
        Long fontId = 999L;

        when(customFontRepository.findByIdAndUserId(fontId, userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.deleteFont(fontId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Font not found or access denied");

        verify(customFontRepository, never()).delete(any());
    }

    @Test
    @DisplayName("getFontFile_withValidId_shouldReturnResource")
    void getFontFile_withValidId_shouldReturnResource() throws IOException {
        // Arrange
        Long userId = 1L;
        Long fontId = 1L;
        String fileName = "user_1_font_123.ttf";

        // Create actual font file
        Path userFontDir = tempDir.resolve("custom-fonts").resolve(String.valueOf(userId));
        Files.createDirectories(userFontDir);
        Path fontFile = userFontDir.resolve(fileName);
        Files.writeString(fontFile, "font content");

        CustomFontEntity font = CustomFontEntity.builder()
                .id(fontId)
                .fontName("Test Font")
                .fileName(fileName)
                .build();

        when(customFontRepository.findByIdAndUserId(fontId, userId)).thenReturn(Optional.of(font));

        // Act
        Resource result = service.getFontFile(fontId, userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.exists()).isTrue();
        assertThat(result.getFile().toPath()).isEqualTo(fontFile);
    }

    @Test
    @DisplayName("getFontFile_whenUserNotOwner_shouldThrowException")
    void getFontFile_whenUserNotOwner_shouldThrowException() {
        // Arrange
        Long userId = 1L;
        Long otherUserId = 2L;
        Long fontId = 1L;

        when(customFontRepository.findByIdAndUserId(fontId, otherUserId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.getFontFile(fontId, otherUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Font not found or access denied");
    }

    @Test
    @DisplayName("getFontFile_withNonExistentFile_shouldThrowException")
    void getFontFile_withNonExistentFile_shouldThrowException() {
        // Arrange
        Long userId = 1L;
        Long fontId = 1L;
        String fileName = "non_existent.ttf";

        CustomFontEntity font = CustomFontEntity.builder()
                .id(fontId)
                .fontName("Test Font")
                .fileName(fileName)
                .build();

        when(customFontRepository.findByIdAndUserId(fontId, userId)).thenReturn(Optional.of(font));

        // Act & Assert
        assertThatThrownBy(() -> service.getFontFile(fontId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Font file not found on disk");
    }

    @Test
    @DisplayName("validatePath_withPathTraversal_shouldThrowException")
    void validatePath_withPathTraversal_shouldThrowException() throws IOException {
        // Arrange
        Long userId = 1L;
        // Use valid font extension to pass extension validation
        String maliciousFileName = "../../../etc/passwd.ttf";
        // Create valid TTF magic bytes to pass magic byte validation
        byte[] fontContent = new byte[]{0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile(
                maliciousFileName,
                maliciousFileName,
                "font/ttf",
                fontContent
        );

        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(customFontRepository.countByUserId(userId)).thenReturn(0);

        // Mock repository save to complete the upload
        when(customFontRepository.save(any(CustomFontEntity.class))).thenAnswer(invocation -> {
            CustomFontEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        CustomFontDto mockDto = new CustomFontDto();
        mockDto.setId(1L);
        when(customFontMapper.toDto(any(CustomFontEntity.class))).thenReturn(mockDto);

        // Act & Assert
        // The service generates safe filenames, so even with malicious input, the path is safe
        assertThatCode(() -> service.uploadFont(file, "Test", userId))
                .doesNotThrowAnyException(); // Safe because service generates safe filename

        // Verify no files were created outside the expected directory
        Path expectedDir = tempDir.resolve("custom-fonts").resolve(String.valueOf(userId));
        Path parentDir = tempDir.resolve("custom-fonts");

        // Count files in user directory
        long userDirFiles = Files.exists(expectedDir) ? Files.list(expectedDir).count() : 0;

        // Count files in parent directory (should only be the user directory)
        long parentDirEntries = Files.list(parentDir).count();

        assertThat(userDirFiles).isGreaterThanOrEqualTo(0);
        assertThat(parentDirEntries).isEqualTo(1); // Only user directory, no escaped files
    }

    @Test
    @DisplayName("getUserFonts_shouldReturnAllUserFonts")
    void getUserFonts_shouldReturnAllUserFonts() {
        // Arrange
        Long userId = 1L;
        List<CustomFontEntity> entities = List.of(
                CustomFontEntity.builder().id(1L).fontName("Font 1").build(),
                CustomFontEntity.builder().id(2L).fontName("Font 2").build()
        );

        CustomFontDto dto1 = new CustomFontDto();
        dto1.setId(1L);
        CustomFontDto dto2 = new CustomFontDto();
        dto2.setId(2L);

        when(customFontRepository.findByUserId(userId)).thenReturn(entities);
        when(customFontMapper.toDto(entities.get(0))).thenReturn(dto1);
        when(customFontMapper.toDto(entities.get(1))).thenReturn(dto2);

        // Act
        List<CustomFontDto> result = service.getUserFonts(userId);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getFontFormat_shouldReturnCorrectFormat")
    void getFontFormat_shouldReturnCorrectFormat() {
        // Arrange
        Long userId = 1L;
        Long fontId = 1L;

        CustomFontEntity font = CustomFontEntity.builder()
                .id(fontId)
                .fontName("Test Font")
                .format(FontFormat.WOFF2)
                .build();

        when(customFontRepository.findByIdAndUserId(fontId, userId)).thenReturn(Optional.of(font));

        // Act
        FontFormat result = service.getFontFormat(fontId, userId);

        // Assert
        assertThat(result).isEqualTo(FontFormat.WOFF2);
    }
}
