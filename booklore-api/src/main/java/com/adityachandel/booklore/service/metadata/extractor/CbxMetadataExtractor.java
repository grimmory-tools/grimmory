package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.stream.Collectors;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

@Slf4j
@Component
public class CbxMetadataExtractor implements FileMetadataExtractor {

  @Override
  public BookMetadata extractMetadata(File file) {
    String baseName = FilenameUtils.getBaseName(file.getName());
    String lowerName = file.getName().toLowerCase();

    // Non-archive (fallback)
    if (!lowerName.endsWith(".cbz") && !lowerName.endsWith(".cbr") && !lowerName.endsWith(".cb7")) {
      return BookMetadata.builder().title(baseName).build();
    }

    // CBZ path (ZIP)
    if (lowerName.endsWith(".cbz")) {
      try (ZipFile zipFile = new ZipFile(file)) {
        ZipEntry entry = findComicInfoEntry(zipFile);
        if (entry == null) {
          return BookMetadata.builder().title(baseName).build();
        }
        try (InputStream is = zipFile.getInputStream(entry)) {
          Document document = buildSecureDocument(is);
          return mapDocumentToMetadata(document, baseName);
        }
      } catch (Exception e) {
        log.warn("Failed to extract metadata from CBZ", e);
        return BookMetadata.builder().title(baseName).build();
      }
    }

    // CB7 path (7z)
    if (lowerName.endsWith(".cb7")) {
      try (SevenZFile sevenZ = new SevenZFile(file)) {
        SevenZArchiveEntry entry = findSevenZComicInfoEntry(sevenZ);
        if (entry == null) {
          return BookMetadata.builder().title(baseName).build();
        }
        byte[] xmlBytes = readSevenZEntryBytes(sevenZ, entry);
        if (xmlBytes == null) {
          return BookMetadata.builder().title(baseName).build();
        }
        try (InputStream is = new ByteArrayInputStream(xmlBytes)) {
          Document document = buildSecureDocument(is);
          return mapDocumentToMetadata(document, baseName);
        }
      } catch (Exception e) {
        log.warn("Failed to extract metadata from CB7", e);
        return BookMetadata.builder().title(baseName).build();
      }
    }

    // CBR path (RAR)
    Archive archive = null;
    try {
      archive = new Archive(file);
      FileHeader header = findComicInfoHeader(archive);
      if (header == null) {
        return BookMetadata.builder().title(baseName).build();
      }
      byte[] xmlBytes = readRarEntryBytes(archive, header);
      if (xmlBytes == null) {
        return BookMetadata.builder().title(baseName).build();
      }
      try (InputStream is = new ByteArrayInputStream(xmlBytes)) {
        Document document = buildSecureDocument(is);
        return mapDocumentToMetadata(document, baseName);
      }
    } catch (Exception e) {
      log.warn("Failed to extract metadata from CBR", e);
      return BookMetadata.builder().title(baseName).build();
      } finally {
      try { if (archive != null) archive.close(); } catch (Exception ignore) {}
    }
  }

  private ZipEntry findComicInfoEntry(ZipFile zipFile) {
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      String name = entry.getName();
      if ("comicinfo.xml".equalsIgnoreCase(name)) {
        return entry;
      }
    }
    return null;
  }

  private Document buildSecureDocument(InputStream is) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    factory.setExpandEntityReferences(false);
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(is);
  }

  private BookMetadata mapDocumentToMetadata(
    Document document,
    String fallbackTitle
  ) {
    BookMetadata.BookMetadataBuilder builder = BookMetadata.builder();

    String title = getTextContent(document, "Title");
    builder.title(title == null || title.isBlank() ? fallbackTitle : title);

    builder.description(
      coalesce(
        getTextContent(document, "Summary"),
        getTextContent(document, "Description")
      )
    );
    builder.publisher(getTextContent(document, "Publisher"));
    builder.seriesName(getTextContent(document, "Series"));
    builder.seriesNumber(parseFloat(getTextContent(document, "Number")));
    builder.seriesTotal(parseInteger(getTextContent(document, "Count")));
    builder.publishedDate(
      parseDate(
        getTextContent(document, "Year"),
        getTextContent(document, "Month"),
        getTextContent(document, "Day")
      )
    );
    builder.pageCount(
      parseInteger(
        coalesce(
          getTextContent(document, "PageCount"),
          getTextContent(document, "Pages")
        )
      )
    );
    builder.language(getTextContent(document, "LanguageISO"));

    Set<String> authors = new HashSet<>();
    authors.addAll(splitValues(getTextContent(document, "Writer")));
    authors.addAll(splitValues(getTextContent(document, "Penciller")));
    authors.addAll(splitValues(getTextContent(document, "Inker")));
    authors.addAll(splitValues(getTextContent(document, "Colorist")));
    authors.addAll(splitValues(getTextContent(document, "Letterer")));
    authors.addAll(splitValues(getTextContent(document, "CoverArtist")));
    if (!authors.isEmpty()) {
      builder.authors(authors);
    }

    Set<String> categories = new HashSet<>();
    categories.addAll(splitValues(getTextContent(document, "Genre")));
    categories.addAll(splitValues(getTextContent(document, "Tags")));
    if (!categories.isEmpty()) {
      builder.categories(categories);
    }

    return builder.build();
  }

  private String getTextContent(Document document, String tag) {
    NodeList nodes = document.getElementsByTagName(tag);
    if (nodes.getLength() == 0) {
      return null;
    }
    return nodes.item(0).getTextContent().trim();
  }

  private String coalesce(String a, String b) {
    return (a != null && !a.isBlank())
      ? a
      : (b != null && !b.isBlank() ? b : null);
  }

  private Set<String> splitValues(String value) {
    if (value == null) {
      return new HashSet<>();
    }
    return Arrays.stream(value.split("[,;]"))
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .collect(Collectors.toSet());
  }

  private Integer parseInteger(String value) {
    try {
      return (value == null || value.isBlank())
        ? null
        : Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Float parseFloat(String value) {
    try {
      return (value == null || value.isBlank())
        ? null
        : Float.parseFloat(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private LocalDate parseDate(String year, String month, String day) {
    Integer y = parseInteger(year);
    Integer m = parseInteger(month);
    Integer d = parseInteger(day);
    if (y == null) {
      return null;
    }
    if (m == null) {
      m = 1;
    }
    if (d == null) {
      d = 1;
    }
    try {
      return LocalDate.of(y, m, d);
    } catch (Exception e) {
      return null;
    }
  }

  public BookMetadata extractFromComicInfoXml(File xmlFile) {
      try (InputStream is = new FileInputStream(xmlFile)) {
          Document document = buildSecureDocument(is);
          String fallbackTitle = xmlFile.getParentFile() != null
                  ? xmlFile.getParentFile().getName()
                  : xmlFile.getName();
          return mapDocumentToMetadata(document, fallbackTitle);
      } catch (Exception e) {
          log.warn("Failed to parse ComicInfo.xml: {}", e.getMessage());
          String fallbackTitle = xmlFile.getParentFile() != null
                  ? xmlFile.getParentFile().getName()
                  : xmlFile.getName();
          return BookMetadata.builder().title(fallbackTitle).build();
      }
  }  

  @Override
  public byte[] extractCover(File file) {
    String lowerName = file.getName().toLowerCase();

    // Non-archive fallback
    if (!lowerName.endsWith(".cbz") && !lowerName.endsWith(".cbr") && !lowerName.endsWith(".cb7")) {
      return generatePlaceholderCover(250, 350);
    }

    // CBZ path
    if (lowerName.endsWith(".cbz")) {
      try (ZipFile zipFile = new ZipFile(file)) {
        ZipEntry coverEntry = findFrontCoverEntry(zipFile);
        if (coverEntry != null) {
          try (InputStream is = zipFile.getInputStream(coverEntry)) {
            return is.readAllBytes();
          }
        } else {
          // Fallback: first image after sorting alphabetically
          ZipEntry firstImage = findFirstAlphabeticalImageEntry(zipFile);
          if (firstImage != null) {
            try (InputStream is2 = zipFile.getInputStream(firstImage)) {
              return is2.readAllBytes();
            }
          }
        }
      } catch (Exception e) {
        log.warn("Failed to extract cover image from CBZ", e);
        return generatePlaceholderCover(250, 350);
      }
    }

    // CB7 path
    if (lowerName.endsWith(".cb7")) {
      try (SevenZFile sevenZ = new SevenZFile(file)) {
        // Try via ComicInfo.xml first
        SevenZArchiveEntry ci = findSevenZComicInfoEntry(sevenZ);
        if (ci != null) {
          byte[] xmlBytes = readSevenZEntryBytes(sevenZ, ci);
          if (xmlBytes != null) {
            try (InputStream is = new ByteArrayInputStream(xmlBytes)) {
              Document document = buildSecureDocument(is);
              String imageName = findFrontCoverImageName(document);
              if (imageName != null) {
                SevenZArchiveEntry byName = findSevenZEntryByName(sevenZ, imageName);
                if (byName != null) {
                  return readSevenZEntryBytes(sevenZ, byName);
                }
                try {
                  int index = Integer.parseInt(imageName);
                  SevenZArchiveEntry byIndex = findSevenZImageEntryByIndex(sevenZ, index);
                  if (byIndex != null) {
                    return readSevenZEntryBytes(sevenZ, byIndex);
                  }
                } catch (NumberFormatException ignore) {
                  // continue to fallback
                }
              }
            }
          }
        }

        // Fallback: first image alphabetically
        SevenZArchiveEntry first = findFirstAlphabeticalSevenZImageEntry(sevenZ);
        if (first != null) {
          return readSevenZEntryBytes(sevenZ, first);
        }
      } catch (Exception e) {
        log.warn("Failed to extract cover image from CB7", e);
        return generatePlaceholderCover(250, 350);
      }
    }

    // CBR path
    Archive archive = null;
    try {
      archive = new Archive(file);

      // Try via ComicInfo.xml first
      FileHeader comicInfo = findComicInfoHeader(archive);
      if (comicInfo != null) {
        byte[] xmlBytes = readRarEntryBytes(archive, comicInfo);
        if (xmlBytes != null) {
          try (InputStream is = new ByteArrayInputStream(xmlBytes)) {
            Document document = buildSecureDocument(is);
            String imageName = findFrontCoverImageName(document);
            if (imageName != null) {
              FileHeader byName = findRarHeaderByName(archive, imageName);
              if (byName != null) {
                return readRarEntryBytes(archive, byName);
              }
              try {
                int index = Integer.parseInt(imageName);
                FileHeader byIndex = findRarImageHeaderByIndex(archive, index);
                if (byIndex != null) {
                  return readRarEntryBytes(archive, byIndex);
                }
              } catch (NumberFormatException ignore) {
                // ignore and continue fallback
              }
            }
          }
        }
      }

      // Fallback: first image in alphabetical order
      FileHeader firstImage = findFirstAlphabeticalImageHeader(archive);
      if (firstImage != null) {
        return readRarEntryBytes(archive, firstImage);
      }
    } catch (Exception e) {
      log.warn("Failed to extract cover image from CBR", e);
      return generatePlaceholderCover(250, 350);
    } finally {
      try { if (archive != null) archive.close(); } catch (Exception ignore) {}
    }

    return generatePlaceholderCover(250, 350);
  }

  private ZipEntry findFrontCoverEntry(ZipFile zipFile) {
    ZipEntry comicInfoEntry = findComicInfoEntry(zipFile);
    if (comicInfoEntry != null) {
      try (InputStream is = zipFile.getInputStream(comicInfoEntry)) {
        Document document = buildSecureDocument(is);
        String imageName = findFrontCoverImageName(document);
        if (imageName != null) {
          ZipEntry byName = zipFile.getEntry(imageName);
          if (byName != null) {
            return byName;
          }
          try {
            int index = Integer.parseInt(imageName);
            ZipEntry byIndex = findImageEntryByIndex(zipFile, index);
            if (byIndex != null) {
              return byIndex;
            }
          } catch (NumberFormatException ignore) {
            // ignore
          }
        }
      } catch (Exception e) {
        log.warn("Failed to parse ComicInfo.xml for cover", e);
      }
    }
    ZipEntry firstImage = findFirstAlphabeticalImageEntry(zipFile);
    return firstImage;
  }

  private ZipEntry findImageEntryByIndex(ZipFile zipFile, int index) {
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    int count = 0;
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if (!entry.isDirectory() && isImageEntry(entry.getName())) {
        if (count == index) {
          return entry;
        }
        count++;
      }
    }
    return null;
  }

  private String findFrontCoverImageName(Document document) {
    NodeList pages = document.getElementsByTagName("Page");
    for (int i = 0; i < pages.getLength(); i++) {
      org.w3c.dom.Node node = pages.item(i);
      if (node instanceof org.w3c.dom.Element) {
        org.w3c.dom.Element page = (org.w3c.dom.Element) node;
        String type = page.getAttribute("Type");
        if (type != null && type.equalsIgnoreCase("FrontCover")) {
          String imageFile = page.getAttribute("ImageFile");
          if (imageFile != null && !imageFile.isBlank()) {
            return imageFile.trim();
          }
          String image = page.getAttribute("Image");
          if (image != null && !image.isBlank()) {
            return image.trim();
          }
        }
      }
    }
    return null;
  }

  private boolean isImageEntry(String name) {
    String lower = name.toLowerCase();
    return (
      lower.endsWith(".jpg") ||
      lower.endsWith(".jpeg") ||
      lower.endsWith(".png") ||
      lower.endsWith(".gif") ||
      lower.endsWith(".bmp") ||
      lower.endsWith(".webp")
    );
  }

  private byte[] generatePlaceholderCover(int width, int height) {
    BufferedImage image = new BufferedImage(
      width,
      height,
      BufferedImage.TYPE_INT_RGB
    );
    Graphics2D g = image.createGraphics();

    g.setColor(Color.LIGHT_GRAY);
    g.fillRect(0, 0, width, height);

    g.setColor(Color.DARK_GRAY);
    g.setFont(new Font("SansSerif", Font.BOLD, width / 10));
    FontMetrics fm = g.getFontMetrics();
    String text = "Preview Unavailable";

    int textWidth = fm.stringWidth(text);
    int textHeight = fm.getAscent();
    g.drawString(text, (width - textWidth) / 2, (height + textHeight) / 2);

    g.dispose();

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      ImageIO.write(image, "jpg", baos);
      return baos.toByteArray();
    } catch (IOException e) {
      log.warn("Failed to generate placeholder image", e);
      return null;
    }
  }


  // ==== RAR (.cbr) helpers ====
  private FileHeader findComicInfoHeader(Archive archive) {
    if (archive == null) return null;
    for (FileHeader fh : archive.getFileHeaders()) {
      String name = fh.getFileName();
      if (name == null) continue;
      String base = baseName(name);
      if ("comicinfo.xml".equalsIgnoreCase(base)) {
        return fh;
      }
    }
    return null;
  }

  private FileHeader findRarHeaderByName(Archive archive, String imageName) {
    if (archive == null || imageName == null) return null;
    for (FileHeader fh : archive.getFileHeaders()) {
      String name = fh.getFileName();
      if (name == null) continue;
      if (name.equalsIgnoreCase(imageName)) return fh;
      // also try base-name match to be lenient
      if (baseName(name).equalsIgnoreCase(baseName(imageName))) return fh;
    }
    return null;
  }

  private FileHeader findRarImageHeaderByIndex(Archive archive, int index) {
    int count = 0;
    for (FileHeader fh : archive.getFileHeaders()) {
      if (!fh.isDirectory() && isImageEntry(fh.getFileName())) {
        if (count == index) return fh;
        count++;
      }
    }
    return null;
  }

  private FileHeader findFirstImageHeader(Archive archive) {
    for (FileHeader fh : archive.getFileHeaders()) {
      if (!fh.isDirectory() && isImageEntry(fh.getFileName())) {
        return fh;
      }
    }
    return null;
  }

  private byte[] readRarEntryBytes(Archive archive, FileHeader header) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      archive.extractFile(header, baos);
      return baos.toByteArray();
    } catch (Exception e) {
      log.warn("Failed to read RAR entry bytes for {}", header != null ? header.getFileName() : "<null>", e);
      return null;
    }
  }

  private String baseName(String path) {
    if (path == null) return null;
    int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return slash >= 0 ? path.substring(slash + 1) : path;
  }  

  private FileHeader findFirstAlphabeticalImageHeader(Archive archive) {
    if (archive == null) return null;
    List<FileHeader> images = new ArrayList<>();
    for (FileHeader fh : archive.getFileHeaders()) {
      if (fh == null || fh.isDirectory()) continue;
      String name = fh.getFileName();
      if (name == null) continue;
      if (isImageEntry(name)) {
        images.add(fh);
      }
    }
    if (images.isEmpty()) return null;
    images.sort(Comparator.comparing(
        fh -> fh.getFileName() == null ? "" : fh.getFileName(),
        String.CASE_INSENSITIVE_ORDER
    ));
    return images.get(0);
  }

  private ZipEntry findFirstAlphabeticalImageEntry(ZipFile zipFile) {
    List<ZipEntry> images = new ArrayList<>();
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry e = entries.nextElement();
      if (!e.isDirectory() && isImageEntry(e.getName())) {
        images.add(e);
      }
    }
    if (images.isEmpty()) return null;
    images.sort(Comparator.comparing(
        entry -> entry.getName() == null ? "" : entry.getName(),
        String.CASE_INSENSITIVE_ORDER
    ));
    return images.get(0);
  }
  
  // ==== 7z (.cb7) helpers ====
  private SevenZArchiveEntry findSevenZComicInfoEntry(SevenZFile sevenZ) throws IOException {
    for (SevenZArchiveEntry e : sevenZ.getEntries()) {
      if (e == null || e.isDirectory()) continue;
      String name = e.getName();
      if (name != null && name.equalsIgnoreCase("ComicInfo.xml")) {
        return e;
      }
    }
    return null;
  }

  private SevenZArchiveEntry findSevenZEntryByName(SevenZFile sevenZ, String imageName) throws IOException {
    if (imageName == null) return null;
    for (SevenZArchiveEntry e : sevenZ.getEntries()) {
      if (e == null || e.isDirectory()) continue;
      String name = e.getName();
      if (name == null) continue;
      if (name.equalsIgnoreCase(imageName)) return e;
      // also allow base-name match
      if (baseName(name).equalsIgnoreCase(baseName(imageName))) return e;
    }
    return null;
  }

  private SevenZArchiveEntry findSevenZImageEntryByIndex(SevenZFile sevenZ, int index) throws IOException {
    int count = 0;
    for (SevenZArchiveEntry e : sevenZ.getEntries()) {
      if (!e.isDirectory() && isImageEntry(e.getName())) {
        if (count == index) return e;
        count++;
      }
    }
    return null;
  }

  private SevenZArchiveEntry findFirstAlphabeticalSevenZImageEntry(SevenZFile sevenZ) throws IOException {
    List<SevenZArchiveEntry> images = new ArrayList<>();
    for (SevenZArchiveEntry e : sevenZ.getEntries()) {
      if (!e.isDirectory() && isImageEntry(e.getName())) {
        images.add(e);
      }
    }
    if (images.isEmpty()) return null;
    images.sort(Comparator.comparing(
        entry -> entry.getName() == null ? "" : entry.getName(),
        String.CASE_INSENSITIVE_ORDER
    ));
    return images.get(0);
  }

  private byte[] readSevenZEntryBytes(SevenZFile sevenZ, SevenZArchiveEntry entry) throws IOException {
    try (InputStream is = sevenZ.getInputStream(entry);
         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      if (is == null) return null;
      is.transferTo(baos);
      return baos.toByteArray();
    }
  }
}