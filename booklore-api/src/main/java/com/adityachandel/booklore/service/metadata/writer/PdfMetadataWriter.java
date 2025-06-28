package com.adityachandel.booklore.service.metadata.writer;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.xml.XmpSerializer;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class PdfMetadataWriter implements MetadataWriter {

    @Override
    public void writeMetadataToFile(File file, BookMetadataEntity metadataEntity, String thumbnailUrl, boolean restoreMode, MetadataClearFlags clear) {
        if (!file.exists() || !file.getName().toLowerCase().endsWith(".pdf")) {
            log.warn("Invalid PDF file: {}", file.getAbsolutePath());
            return;
        }

        File tempFile = null;

        try (PDDocument pdf = Loader.loadPDF(file)) {
            pdf.setAllSecurityToBeRemoved(true);
            applyMetadataToDocument(pdf, metadataEntity, restoreMode, clear);
            tempFile = File.createTempFile("pdfmeta-", ".pdf");
            pdf.save(tempFile);
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("Successfully embedded metadata into PDF: {}", file.getName());
        } catch (IOException e) {
            log.warn("Failed to write metadata to PDF {}", file.getName(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @Override
    public BookFileType getSupportedBookType() {
        return BookFileType.PDF;
    }

    private void applyMetadataToDocument(PDDocument pdf, BookMetadataEntity entity, boolean restoreMode, MetadataClearFlags clear) {
        PDDocumentInformation info = pdf.getDocumentInformation();
        MetadataCopyHelper helper = new MetadataCopyHelper(entity);

        helper.copyTitle(restoreMode, clear != null && clear.isTitle(), title -> info.setTitle(title != null ? title : ""));
        helper.copyPublisher(restoreMode, clear != null && clear.isPublisher(), pub -> info.setProducer(pub != null ? pub : ""));
        helper.copyAuthors(restoreMode, clear != null && clear.isAuthors(), authors -> info.setAuthor(authors != null ? String.join(", ", authors) : ""));
        helper.copyCategories(restoreMode, clear != null && clear.isCategories(), cats -> info.setKeywords(cats != null ? String.join(", ", cats) : ""));

        try {
            XMPMetadata xmp = XMPMetadata.createXMPMetadata();
            DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();

            helper.copyTitle(restoreMode, clear != null && clear.isTitle(), title -> dc.setTitle(title != null ? title : ""));
            helper.copyDescription(restoreMode, clear != null && clear.isDescription(), desc -> dc.setDescription(desc != null ? desc : ""));
            helper.copyPublisher(restoreMode, clear != null && clear.isPublisher(), pub -> dc.addPublisher(pub != null ? pub : ""));
            helper.copyLanguage(restoreMode, clear != null && clear.isLanguage(), lang -> dc.addLanguage(lang != null ? lang : ""));
            helper.copyPublishedDate(restoreMode, clear != null && clear.isPublishedDate(), date -> {
                Calendar cal = GregorianCalendar.from(
                        (date != null ? date : ZonedDateTime.now().toLocalDate())
                                .atStartOfDay(ZoneId.systemDefault()));
                dc.addDate(cal);
            });

            helper.copyAuthors(restoreMode, clear != null && clear.isAuthors(), authors -> {
                (authors != null ? authors : List.of("")).forEach(dc::addCreator);
            });

            helper.copyCategories(restoreMode, clear != null && clear.isCategories(), cats -> {
                (cats != null ? cats : List.of("")).forEach(dc::addSubject);
            });

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new XmpSerializer().serialize(xmp, baos, true);
            byte[] baseXmpBytes = baos.toByteArray();

            byte[] newXmpBytes = addCustomIdentifiersToXmp(baseXmpBytes, entity, helper, restoreMode, clear);

            byte[] existingXmpBytes = null;
            PDMetadata existingMetadata = pdf.getDocumentCatalog().getMetadata();
            if (existingMetadata != null) {
                try {
                    existingXmpBytes = existingMetadata.toByteArray();
                } catch (IOException ignore) {
                }
            }

            if (!isXmpMetadataDifferent(existingXmpBytes, newXmpBytes)) {
                log.info("XMP metadata unchanged, skipping write");
                return;
            }

            PDMetadata pdMetadata = new PDMetadata(pdf);
            pdMetadata.importXMPMetadata(newXmpBytes);
            pdf.getDocumentCatalog().setMetadata(pdMetadata);

            log.info("XMP metadata updated for PDF");
        } catch (Exception e) {
            log.warn("Failed to embed XMP metadata: {}", e.getMessage(), e);
        }
    }

    private byte[] addCustomIdentifiersToXmp(byte[] xmpBytes, BookMetadataEntity metadata, MetadataCopyHelper helper, boolean restoreMode, MetadataClearFlags clear) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xmpBytes));

        Element rdfRoot = (Element) doc.getElementsByTagNameNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "RDF").item(0);
        if (rdfRoot == null) throw new IllegalStateException("RDF root missing in XMP");

        Element rdfDescription = doc.createElementNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:Description");
        rdfDescription.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xmp", "http://ns.adobe.com/xap/1.0/");
        rdfDescription.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xmpidq", "http://ns.adobe.com/xmp/Identifier/qual/1.0/");
        rdfDescription.setAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:about", "");

        Element xmpIdentifier = doc.createElementNS("http://ns.adobe.com/xap/1.0/", "xmp:Identifier");
        Element rdfBag = doc.createElementNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:Bag");

        helper.copyGoogleId(restoreMode, clear != null && clear.isGoogleId(), id -> appendIdentifier(doc, rdfBag, "google", id != null ? id : ""));
        helper.copyGoodreadsId(restoreMode, clear != null && clear.isGoodreadsId(), id -> appendIdentifier(doc, rdfBag, "goodreads", id != null ? id : ""));
        helper.copyHardcoverId(restoreMode, clear != null && clear.isHardcoverId(), id -> appendIdentifier(doc, rdfBag, "hardcover", id != null ? id : ""));
        helper.copyAsin(restoreMode, clear != null && clear.isAsin(), id -> appendIdentifier(doc, rdfBag, "amazon", id != null ? id : ""));
        helper.copyIsbn13(restoreMode, clear != null && clear.isIsbn13(), id -> appendIdentifier(doc, rdfBag, "isbn", id != null ? id : ""));

        if (rdfBag.hasChildNodes()) {
            xmpIdentifier.appendChild(rdfBag);
            rdfDescription.appendChild(xmpIdentifier);
        }

        rdfDescription.appendChild(createSimpleElement(doc, "xmp:MetadataDate", ZonedDateTime.now().toString()));
        rdfDescription.appendChild(createSimpleElement(doc, "xmp:CreateDate", metadata.getPublishedDate() != null
                ? metadata.getPublishedDate().atStartOfDay(ZoneId.systemDefault()).toString()
                : ZonedDateTime.now().toString()));
        rdfDescription.appendChild(createSimpleElement(doc, "xmp:CreatorTool", "Booklore"));
        rdfDescription.appendChild(createSimpleElement(doc, "xmp:ModifyDate", ZonedDateTime.now().toString()));

        rdfRoot.appendChild(rdfDescription);

        Element calibreDescription = doc.createElementNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:Description");
        calibreDescription.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:calibre", "http://calibre-ebook.com/xmp-namespace");
        calibreDescription.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:calibreSI", "http://calibre-ebook.com/xmp-namespace-series-index");
        calibreDescription.setAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:about", "");

        helper.copyPersonalRating(restoreMode, clear != null && clear.isPersonalRating(), rating -> {
            String value = (rating != null) ? String.valueOf((int) Math.round(rating)) : "";
            calibreDescription.appendChild(createSimpleElement(doc, "calibre:rating", value));
        });

        helper.copySeriesName(restoreMode, clear != null && clear.isSeriesName(), series -> {
            Element seriesElem = doc.createElementNS("http://calibre-ebook.com/xmp-namespace", "calibre:series");
            seriesElem.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:calibreSI", "http://calibre-ebook.com/xmp-namespace-series-index");
            seriesElem.setAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:parseType", "Resource");

            Element valueElem = doc.createElementNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:value");
            valueElem.setTextContent(series != null ? series : "");
            seriesElem.appendChild(valueElem);

            helper.copySeriesNumber(restoreMode, clear != null && clear.isSeriesNumber(), index -> {
                Element indexElem = doc.createElementNS("http://calibre-ebook.com/xmp-namespace-series-index", "calibreSI:series_index");
                indexElem.setTextContent(index != null ? String.format("%.2f", index) : "0.00");
                seriesElem.appendChild(indexElem);
            });

            calibreDescription.appendChild(seriesElem);
        });

        rdfRoot.appendChild(calibreDescription);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Transformer tf = TransformerFactory.newInstance().newTransformer();
        tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        tf.setOutputProperty(OutputKeys.INDENT, "yes");
        tf.transform(new DOMSource(doc), new StreamResult(baos));
        return baos.toByteArray();
    }

    private void appendIdentifier(Document doc, Element bag, String scheme, String value) {
        if (StringUtils.isBlank(value)) return;
        Element li = doc.createElementNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:li");
        li.setAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:parseType", "Resource");

        Element schemeElem = doc.createElementNS("http://ns.adobe.com/xmp/Identifier/qual/1.0/", "xmpidq:Scheme");
        schemeElem.setTextContent(scheme);

        Element valueElem = doc.createElementNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:value");
        valueElem.setTextContent(value);

        li.appendChild(schemeElem);
        li.appendChild(valueElem);
        bag.appendChild(li);
    }

    private Element createSimpleElement(Document doc, String name, String content) {
        String namespace = name.startsWith("calibre:")
                ? "http://calibre-ebook.com/xmp-namespace"
                : "http://ns.adobe.com/xap/1.0/";

        Element el = doc.createElementNS(namespace, name);
        el.setTextContent(content);
        return el;
    }

    private boolean isXmpMetadataDifferent(byte[] existingBytes, byte[] newBytes) {
        if (existingBytes == null || newBytes == null) return true;
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc1 = builder.parse(new ByteArrayInputStream(existingBytes));
            Document doc2 = builder.parse(new ByteArrayInputStream(newBytes));
            return !Objects.equals(
                    doc1.getDocumentElement().getTextContent().trim(),
                    doc2.getDocumentElement().getTextContent().trim()
            );
        } catch (Exception e) {
            log.warn("XMP diff failed: {}", e.getMessage());
            return true;
        }
    }
}