package org.booklore.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@UtilityClass
public class SecureXmlUtils {

    // DocumentBuilderFactory is thread-safe after configuration cache one per namespace-aware mode
    private static final DocumentBuilderFactory NS_AWARE_FACTORY;
    private static final DocumentBuilderFactory NON_NS_AWARE_FACTORY;
    private static final String RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String DC_NAMESPACE = "http://purl.org/dc/elements/1.1/";
    private static final Pattern RDF_ROOT_PATTERN = Pattern.compile("<rdf:RDF\\b([^>]*)>");
    private static final Pattern RDF_NAMESPACE_PATTERN = Pattern.compile("\\bxmlns:rdf\\s*=");
    private static final Pattern DC_NAMESPACE_PATTERN = Pattern.compile("\\bxmlns:dc\\s*=");
    private static final Pattern DC_PREFIX_USAGE_PATTERN = Pattern.compile("(?:</?dc:|\\sdc:)");

    static {
        try {
            NS_AWARE_FACTORY = buildFactory(true);
            NON_NS_AWARE_FACTORY = buildFactory(false);
        } catch (ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static DocumentBuilderFactory buildFactory(boolean namespaceAware) throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(namespaceAware);

        // Prevent XXE attacks
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        return factory;
    }

    private static DocumentBuilderFactory getFactory(boolean namespaceAware) {
        return namespaceAware ? NS_AWARE_FACTORY : NON_NS_AWARE_FACTORY;
    }

    public static DocumentBuilder createSecureDocumentBuilder(boolean namespaceAware) 
            throws ParserConfigurationException {
        // newDocumentBuilder() is NOT thread-safe must create new builder each time
        return getFactory(namespaceAware).newDocumentBuilder();
    }

    public static Document parseXml(String xml, boolean namespaceAware) throws Exception {
        String normalizedXml = namespaceAware ? normalizeMissingXmpNamespaces(xml) : xml;
        return parseStrict(normalizedXml, namespaceAware);
    }

    public static String normalizeMissingXmpNamespaces(String xml) {
        if (xml == null) {
            return xml;
        }
        Matcher rootMatcher = RDF_ROOT_PATTERN.matcher(xml);
        if (!rootMatcher.find()) {
            return xml;
        }

        String rootAttributes = rootMatcher.group(1);
        StringBuilder missingNamespaces = new StringBuilder();
        if (!RDF_NAMESPACE_PATTERN.matcher(rootAttributes).find()) {
            missingNamespaces.append(" xmlns:rdf=\"").append(RDF_NAMESPACE).append('"');
        }
        if (DC_PREFIX_USAGE_PATTERN.matcher(xml).find()
                && !DC_NAMESPACE_PATTERN.matcher(rootAttributes).find()) {
            missingNamespaces.append(" xmlns:dc=\"").append(DC_NAMESPACE).append('"');
        }
        if (missingNamespaces.isEmpty()) {
            return xml;
        }

        String normalizedRoot = "<rdf:RDF" + missingNamespaces + rootAttributes + ">";
        return rootMatcher.replaceFirst(Matcher.quoteReplacement(normalizedRoot));
    }

    private static Document parseStrict(String xml, boolean namespaceAware) throws Exception {
        var builder = createSecureDocumentBuilder(namespaceAware);
        builder.setErrorHandler(new SilentErrorHandler());
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static final class SilentErrorHandler extends DefaultHandler {
        @Override
        public void warning(SAXParseException e) {
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    }
}
