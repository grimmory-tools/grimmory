package org.booklore.opf;

import org.booklore.util.SecureXmlUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.StringReader;

final class OpfXmlParser {

    private static final String RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    private OpfXmlParser() {
    }

    static Document parse(String xml) throws Exception {
        try {
            return parseStrict(xml);
        } catch (SAXParseException e) {
            if (!isMissingRdfNamespace(e, xml)) {
                throw e;
            }
            return parseStrict(bindMissingRdfNamespace(xml));
        }
    }

    private static Document parseStrict(String xml) throws Exception {
        var builder = SecureXmlUtils.createSecureDocumentBuilder(true);
        builder.setErrorHandler(new SilentErrorHandler());
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static boolean isMissingRdfNamespace(SAXParseException error, String xml) {
        return error.getMessage() != null
                && error.getMessage().contains("The prefix \"rdf\" for element \"rdf:RDF\" is not bound")
                && xml.contains("<rdf:RDF")
                && !xml.contains("xmlns:rdf=");
    }

    private static String bindMissingRdfNamespace(String xml) {
        return xml.replaceFirst("<rdf:RDF(\\s|>)", "<rdf:RDF xmlns:rdf=\"" + RDF_NAMESPACE + "\"$1");
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
