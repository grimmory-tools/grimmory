package org.booklore.util.koreader;

import java.util.List;

/**
 * Abstraction over an HTML document structure, allowing CfiConverter to work without coupling to a
 * specific DOM/parser library (Jsoup, org.w3c.dom, etc.).
 *
 * <p>Implementations should represent elements as opaque objects and provide navigation methods.
 */
public interface DocumentNavigator {

  /** Returns the {@code <body>} element, or null if none exists. */
  Object getBody();

  /** Returns the direct child elements of the given element (no text nodes). */
  List<Object> getChildElements(Object element);

  /** Returns all descendant elements matching the given tag name (case-insensitive). */
  List<Object> getElementsByTag(Object root, String tagName);

  /** Returns the tag name of the given element (lowercase). */
  String getTagName(Object element);

  /** Returns the parent element, or null if this is the root. */
  Object getParent(Object element);

  /**
   * Collects all text content from the element and its descendants, returning each text node's
   * content as a separate string in document order. Empty text nodes should be omitted.
   */
  List<String> collectTextContent(Object element);
}
