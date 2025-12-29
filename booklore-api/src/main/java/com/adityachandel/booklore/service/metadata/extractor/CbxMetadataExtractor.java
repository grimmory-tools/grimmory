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
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.List;
import java.util.ArrayList;
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

    private static final Pattern LEADING_ZEROS_PATTERN = Pattern.compile("^0+");
    private static final Pattern COMMA_SEMICOLON_PATTERN = Pattern.compile("[,;]");

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
      try (SevenZFile sevenZ = SevenZFile.builder().setFile(file).get()) {
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
        try (Archive archive = new Archive(file)) {
            try {
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
            }
        } catch (Exception ignore) {
        }
        return BookMetadata.builder().title(baseName).build();
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
    try {
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    } catch (Exception ex) {
      log.debug("XML factory secure feature not supported: {}", ex.getMessage());
    }
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
    return Arrays.stream(COMMA_SEMICOLON_PATTERN.split(value))
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
        // Try front cover via ComicInfo
        ZipEntry coverEntry = findFrontCoverEntry(zipFile);
        if (coverEntry != null) {
          try (InputStream is = zipFile.getInputStream(coverEntry)) {
            byte[] bytes = is.readAllBytes();
            if (canDecode(bytes)) return bytes;
          }
        }
        // Fallback: iterate images alphabetically until a decodable one is found
        ZipEntry firstImage = findFirstAlphabeticalImageEntry(zipFile);
        if (firstImage != null) {
          // Build a sorted list and iterate for decodable formats
          java.util.List<ZipEntry> images = listZipImageEntries(zipFile);
          for (ZipEntry e : images) {
            try (InputStream is = zipFile.getInputStream(e)) {
              byte[] bytes = is.readAllBytes();
              if (canDecode(bytes)) return bytes;
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
      try (SevenZFile sevenZ = SevenZFile.builder().setFile(file).get()) {
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
                  byte[] bytes = readSevenZEntryBytes(sevenZ, byName);
                  if (canDecode(bytes)) return bytes;
                }
                try {
                  int index = Integer.parseInt(imageName);
                  SevenZArchiveEntry byIndex = findSevenZImageEntryByIndex(sevenZ, index);
                  if (byIndex != null) {
                    byte[] bytes = readSevenZEntryBytes(sevenZ, byIndex);
                    if (canDecode(bytes)) return bytes;
                  }
                  if (index > 0) {
                    SevenZArchiveEntry offByOne = findSevenZImageEntryByIndex(sevenZ, index - 1);
                    if (offByOne != null) {
                      byte[] bytes = readSevenZEntryBytes(sevenZ, offByOne);
                      if (canDecode(bytes)) return bytes;
                    }
                  }
                } catch (NumberFormatException ignore) {
                  // continue to fallback
                }
              }
            }
          }
        }

        // Fallback: iterate images alphabetically until a decodable one is found
        SevenZArchiveEntry first = findFirstAlphabeticalSevenZImageEntry(sevenZ);
        if (first != null) {
          java.util.List<SevenZArchiveEntry> images = listSevenZImageEntries(sevenZ);
          for (SevenZArchiveEntry e : images) {
            byte[] bytes = readSevenZEntryBytes(sevenZ, e);
            if (canDecode(bytes)) return bytes;
          }
        }
      } catch (Exception e) {
        log.warn("Failed to extract cover image from CB7", e);
        return generatePlaceholderCover(250, 350);
      }
    }

    // CBR path
      try (Archive archive = new Archive(file)) {
          try {

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
                                  byte[] bytes = readRarEntryBytes(archive, byName);
                                  if (canDecode(bytes)) return bytes;
                              }
                              try {
                                  int index = Integer.parseInt(imageName);
                                  FileHeader byIndex = findRarImageHeaderByIndex(archive, index);
                                  if (byIndex != null) {
                                      byte[] bytes = readRarEntryBytes(archive, byIndex);
                                      if (canDecode(bytes)) return bytes;
                                  }
                                  if (index > 0) {
                                      FileHeader offByOne = findRarImageHeaderByIndex(archive, index - 1);
                                      if (offByOne != null) {
                                          byte[] bytes = readRarEntryBytes(archive, offByOne);
                                          if (canDecode(bytes)) return bytes;
                                      }
                                  }
                              } catch (NumberFormatException ignore) {
                                  // ignore and continue fallback
                              }
                          }
                      }
                  }
              }

              // Fallback: iterate images alphabetically until a decodable one is found
              FileHeader firstImage = findFirstAlphabeticalImageHeader(archive);
              if (firstImage != null) {
                  List<FileHeader> images = listRarImageHeaders(archive);
                  for (FileHeader fh : images) {
                      byte[] bytes = readRarEntryBytes(archive, fh);
                      if (canDecode(bytes)) return bytes;
                  }
              }
          } catch (Exception e) {
              log.warn("Failed to extract cover image from CBR", e);
              return generatePlaceholderCover(250, 350);
          }
      } catch (Exception ignore) {
      }

    return generatePlaceholderCover(250, 350);
  }

  private boolean canDecode(byte[] bytes) {
    if (bytes == null || bytes.length == 0) return false;
    try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
      BufferedImage img = ImageIO.read(bais);
      return img != null;
    } catch (IOException e) {
      return false;
    }
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
          // also try base-name match for archives with directories or odd encodings
          String imageBase = baseName(imageName);
          java.util.Enumeration<? extends ZipEntry> it = zipFile.entries();
          while (it.hasMoreElements()) {
            ZipEntry e = it.nextElement();
            if (!e.isDirectory() && isImageEntry(e.getName())) {
              if (baseName(e.getName()).equalsIgnoreCase(imageBase)) {
                return e;
              }
            }
          }
          try {
            int index = Integer.parseInt(imageName);
            ZipEntry byIndex = findImageEntryByIndex(zipFile, index);
            if (byIndex != null) {
              return byIndex;
            }
            if (index > 0) {
              ZipEntry offByOne = findImageEntryByIndex(zipFile, index - 1);
              if (offByOne != null) return offByOne;
            }
          } catch (NumberFormatException ignore) {
            // ignore
          }
        }
      } catch (Exception e) {
        log.warn("Failed to parse ComicInfo.xml for cover", e);
      }
    }
    // Heuristic filenames before generic fallback
    ZipEntry heuristic = findHeuristicCover(zipFile);
    if (heuristic != null) return heuristic;
    return findFirstAlphabeticalImageEntry(zipFile);
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
      if (node instanceof org.w3c.dom.Element page) {
          String type = page.getAttribute("Type");
        if (type != null && "FrontCover".equalsIgnoreCase(type)) {
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
    if (!isContentEntry(name)) return false;
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

  private boolean isContentEntry(String name) {
    if (name == null) return false;
    String norm = name.replace('\\', '/');
    if (norm.startsWith("__MACOSX/") || norm.contains("/__MACOSX/")) return false;
    String base = baseName(norm);
    if (base.startsWith(".")) return false;
    if (".ds_store".equalsIgnoreCase(base)) return false;
    return true;
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
    images.sort((a, b) -> naturalCompare(a.getFileName(), b.getFileName()));
    return images.getFirst();
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
    images.sort((a, b) -> naturalCompare(a.getName(), b.getName()));
    return images.getFirst();
  }
  
  // ==== 7z (.cb7) helpers ====
  private SevenZArchiveEntry findSevenZComicInfoEntry(SevenZFile sevenZ) {
    for (SevenZArchiveEntry e : sevenZ.getEntries()) {
      if (e == null || e.isDirectory()) continue;
      String name = e.getName();
      if (name != null && "ComicInfo.xml".equalsIgnoreCase(name)) {
        return e;
      }
    }
    return null;
  }

  private SevenZArchiveEntry findSevenZEntryByName(SevenZFile sevenZ, String imageName) {
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

  private SevenZArchiveEntry findSevenZImageEntryByIndex(SevenZFile sevenZ, int index) {
    int count = 0;
    for (SevenZArchiveEntry e : sevenZ.getEntries()) {
      if (!e.isDirectory() && isImageEntry(e.getName())) {
        if (count == index) return e;
        count++;
      }
    }
    return null;
  }

  private SevenZArchiveEntry findFirstAlphabeticalSevenZImageEntry(SevenZFile sevenZ) {
    List<SevenZArchiveEntry> images = new ArrayList<>();
    for (SevenZArchiveEntry e : sevenZ.getEntries()) {
      if (!e.isDirectory() && isImageEntry(e.getName())) {
        images.add(e);
      }
    }
    if (images.isEmpty()) return null;
    images.sort((a, b) -> naturalCompare(a.getName(), b.getName()));
    return images.getFirst();
  }

  private byte[] readSevenZEntryBytes(SevenZFile sevenZ, SevenZArchiveEntry entry) throws IOException {
    try (InputStream is = sevenZ.getInputStream(entry);
         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      if (is == null) return null;
      is.transferTo(baos);
      return baos.toByteArray();
    }
  }

  private java.util.List<ZipEntry> listZipImageEntries(ZipFile zipFile) {
    java.util.List<ZipEntry> images = new java.util.ArrayList<>();
    java.util.Enumeration<? extends ZipEntry> en = zipFile.entries();
    while (en.hasMoreElements()) {
      ZipEntry e = en.nextElement();
      if (!e.isDirectory() && isImageEntry(e.getName())) images.add(e);
    }
    images.sort((a, b) -> naturalCompare(a.getName(), b.getName()));
    // Heuristic preferred names first
    images.sort((a, b) -> Boolean.compare(!likelyCoverName(baseName(a.getName())), !likelyCoverName(baseName(b.getName()))));
    return images;
  }

  private java.util.List<SevenZArchiveEntry> listSevenZImageEntries(SevenZFile sevenZ) {
    java.util.List<SevenZArchiveEntry> images = new java.util.ArrayList<>();
    for (SevenZArchiveEntry e : sevenZ.getEntries()) {
      if (!e.isDirectory() && isImageEntry(e.getName())) images.add(e);
    }
    images.sort((a, b) -> naturalCompare(a.getName(), b.getName()));
    images.sort((a, b) -> Boolean.compare(!likelyCoverName(baseName(a.getName())), !likelyCoverName(baseName(b.getName()))));
    return images;
  }

  private java.util.List<FileHeader> listRarImageHeaders(Archive archive) {
    java.util.List<FileHeader> images = new java.util.ArrayList<>();
    for (FileHeader fh : archive.getFileHeaders()) {
      if (fh != null && !fh.isDirectory() && isImageEntry(fh.getFileName())) images.add(fh);
    }
    images.sort((a, b) -> naturalCompare(a.getFileName(), b.getFileName()));
    images.sort((a, b) -> Boolean.compare(!likelyCoverName(baseName(a.getFileName())), !likelyCoverName(baseName(b.getFileName()))));
    return images;
  }

  private boolean likelyCoverName(String base) {
    if (base == null) return false;
    String n = base.toLowerCase();
    return n.startsWith("cover") || "folder".equals(n) || n.startsWith("front");
  }

  private int naturalCompare(String a, String b) {
    if (a == null) return b == null ? 0 : -1;
    if (b == null) return 1;
    String s1 = a.toLowerCase();
    String s2 = b.toLowerCase();
    int i = 0, j = 0, n1 = s1.length(), n2 = s2.length();
    while (i < n1 && j < n2) {
      char c1 = s1.charAt(i);
      char c2 = s2.charAt(j);
      if (Character.isDigit(c1) && Character.isDigit(c2)) {
        int i1 = i; while (i1 < n1 && Character.isDigit(s1.charAt(i1))) i1++;
        int j1 = j; while (j1 < n2 && Character.isDigit(s2.charAt(j1))) j1++;
        String num1 = LEADING_ZEROS_PATTERN.matcher(s1.substring(i, i1)).replaceFirst("");
        String num2 = LEADING_ZEROS_PATTERN.matcher(s2.substring(j, j1)).replaceFirst("");
        int cmp = Integer.compare(num1.isEmpty() ? 0 : Integer.parseInt(num1), num2.isEmpty() ? 0 : Integer.parseInt(num2));
        if (cmp != 0) return cmp;
        i = i1; j = j1;
      } else {
        if (c1 != c2) return Character.compare(c1, c2);
        i++; j++;
      }
    }
    return Integer.compare(n1 - i, n2 - j);
  }

  private ZipEntry findHeuristicCover(ZipFile zipFile) {
    java.util.List<ZipEntry> images = listZipImageEntries(zipFile);
    for (ZipEntry e : images) {
      if (likelyCoverName(baseName(e.getName()))) return e;
    }
    return null;
  }
}
