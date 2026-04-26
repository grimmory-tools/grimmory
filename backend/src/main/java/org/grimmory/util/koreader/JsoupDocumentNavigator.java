package org.grimmory.util.koreader;

import org.grimmory.epub4j.cfi.DocumentNavigator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Jsoup-backed implementation of epub4j's {@link DocumentNavigator}, enabling
 * the library's {@link org.grimmory.epub4j.cfi.CfiConverter} to navigate a
 * Jsoup-parsed HTML document.
 */
public class JsoupDocumentNavigator implements DocumentNavigator {

    private final Document document;

    public JsoupDocumentNavigator(Document document) {
        this.document = document;
    }

    @Override
    public Object getBody() {
        return document.body();
    }

    @Override
    public List<Object> getChildElements(Object element) {
        Element el = (Element) element;
        return new ArrayList<>(el.children());
    }

    @Override
    public List<Object> getElementsByTag(Object root, String tagName) {
        Element el = (Element) root;
        return new ArrayList<>(el.getElementsByTag(tagName));
    }

    @Override
    public String getTagName(Object element) {
        Element el = (Element) element;
        return el.tagName().toLowerCase();
    }

    @Override
    public Object getParent(Object element) {
        Element el = (Element) element;
        return el.parent();
    }

    @Override
    public List<String> collectTextContent(Object element) {
        List<String> texts = new ArrayList<>();
        collectTexts((Node) element, texts);
        return texts;
    }

    private void collectTexts(Node node, List<String> texts) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode textNode) {
                String text = textNode.text();
                if (!text.isEmpty()) {
                    texts.add(text);
                }
            } else if (child instanceof Element) {
                collectTexts(child, texts);
            }
        }
    }
}
