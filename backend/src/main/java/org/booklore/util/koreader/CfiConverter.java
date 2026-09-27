/*
 *
 * Architecture inspired by epub.js (BSD-2-Clause, futurepress): CFI strings are
 * first parsed into a typed data model (CfiExpression / CfiParser), then resolved
 * against the document via a pluggable DocumentNavigator.
 *
 * The CFI grammar follows the EPUB CFI 1.1 specification (IDPF / W3C public standard).
 *
 * The KOReader XPointer format (/body/DocFragment[N]/body/.../text().O) follows
 * conventions documented in the KOReader / CREngine source code (AGPL-3.0).
 */
package org.booklore.util.koreader;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Bidirectional converter between EPUB CFI expressions and KOReader-style XPointer strings.
 *
 * <p>Typical lifecycle:
 *
 * <pre>{@code
 * CfiConverter conv = new CfiConverter(navigator, spineIndex);
 * XPointerResult xp = conv.cfiToXPointer("epubcfi(/6/2!/4/2/2)");
 * String cfi = conv.xPointerToCfi("/body/DocFragment[1]/body/div[1]/p[1]");
 * }</pre>
 *
 * <p>Static helpers ({@link #extractSpineIndex}, {@link #normalizeProgressXPointer}) operate
 * without a document and can be called before creating a converter instance.
 */
public class CfiConverter {

  /**
   * Tags considered "inline" when mapping text offsets. Text within these elements is treated as
   * part of the surrounding block for XPointer purposes.
   */
  private static final Set<String> INLINE_TAGS =
      Set.of(
          "a", "abbr", "b", "bdi", "bdo", "cite", "code", "dfn", "em", "i", "kbd", "mark", "q",
          "rp", "rt", "ruby", "s", "samp", "small", "span", "strong", "sub", "sup", "time", "u",
          "var", "wbr");

  /** KOReader XPointer prefix format: {@code /body/DocFragment[<1-based>]/body}. */
  private static final String XPOINTER_PREFIX = "/body/DocFragment[";

  private static final String XPOINTER_BODY_SUFFIX = "]/body";

  private static final Pattern TEXT_SUFFIX =
      Pattern.compile("/text\\(\\)(?:\\[([0-9]+)\\])?\\.([0-9]+)$");

  private final DocumentNavigator nav;
  private final int spineIdx;

  /**
   * Creates a converter for a specific spine item.
   *
   * @param navigator document structure abstraction
   * @param spineIndex zero-based index of the spine item this document represents
   */
  public CfiConverter(DocumentNavigator navigator, int spineIndex) {
    this.nav = navigator;
    this.spineIdx = spineIndex;
  }

  // -- Static utilities -------------------------------------------------------

  /**
   * Determines the zero-based spine-item index from either a CFI string or a KOReader XPointer.
   *
   * @throws IllegalArgumentException if the format is unrecognised or the value cannot be parsed
   */
  public static int extractSpineIndex(String locator) {
    if (locator == null || locator.isBlank()) {
      throw new IllegalArgumentException("CFI/XPointer string cannot be null or empty");
    }

    if (locator.startsWith("epubcfi(")) {
      CfiExpression expr = CfiParser.parse(locator);
      return expr.spineIndex();
    }

    if (locator.startsWith(XPOINTER_PREFIX)) {
      return parseDocFragmentIndex(locator);
    }

    throw new IllegalArgumentException("Unsupported locator format: " + locator);
  }

  /**
   * Strips text-offset and node-suffix components from a KOReader XPointer, leaving only the
   * structural element path. Useful for reading-progress tracking where character precision is
   * unnecessary.
   *
   * @return the normalised XPointer, or {@code null} when the input is {@code null}
   */
  public static String normalizeProgressXPointer(String xpointer) {
    if (xpointer == null) return null;

    // Remove either text().N or text()[index].N, including legacy node suffixes.
    int textIdx = xpointer.indexOf("/text()");
    if (textIdx >= 0) {
      xpointer = xpointer.substring(0, textIdx);
    }

    // Remove trailing ".N" node suffix (e.g. ".5" on the last segment)
    int lastSlash = xpointer.lastIndexOf('/');
    String tail = lastSlash >= 0 ? xpointer.substring(lastSlash) : xpointer;
    int dotIdx = tail.lastIndexOf('.');
    if (dotIdx > 0) {
      String afterDot = tail.substring(dotIdx + 1);
      if (!afterDot.isEmpty() && afterDot.chars().allMatch(Character::isDigit)) {
        xpointer = xpointer.substring(0, xpointer.length() - afterDot.length() - 1);
      }
    }

    return xpointer;
  }

  // -- Instance conversion methods --------------------------------------------

  /**
   * Resolves a CFI to a KOReader XPointer result.
   *
   * @throws IllegalArgumentException if the CFI is malformed or targets a different spine item
   */
  public XPointerResult cfiToXPointer(String cfi) {
    CfiExpression expr = CfiParser.parse(cfi);

    if (expr.spineIndex() != spineIdx) {
      throw new IllegalArgumentException(
          "CFI spine index "
              + expr.spineIndex()
              + " does not match converter spine index "
              + spineIdx);
    }

    String xp = resolveToXPointer(expr.contentSteps(), expr.charOffset());
    return new XPointerResult(xp, xp, xp);
  }

  /** Converts a single KOReader XPointer to a CFI string. */
  public String xPointerToCfi(String xpointer) {
    return xPointerToCfi(xpointer, null);
  }

  /**
   * Converts one or two KOReader XPointers (point or range) to a CFI string.
   *
   * <p>When {@code endXPointer} is non-blank a range CFI is produced.
   */
  public String xPointerToCfi(String startXPointer, String endXPointer) {
    XPointerParts startParts = decomposeXPointer(startXPointer);
    String startPath = xPointerContentPath(startParts);

    if (endXPointer == null || endXPointer.isBlank()) {
      return wrapCfi(startPath);
    }

    XPointerParts endParts = decomposeXPointer(endXPointer);
    String endPath = xPointerContentPath(endParts);

    if (startPath.equals(endPath)) {
      return wrapCfi(startPath);
    }
    return wrapCfi(startPath + "," + endPath);
  }

  private String xPointerContentPath(XPointerParts parts) {
    var steps = new ArrayList<>(domToCfiSteps(parts.element()));
    if (parts.textStep() != null) {
      steps.add(new CfiExpression.PathStep(parts.textStep(), null));
    }
    return CfiParser.formatContentPath(steps, parts.textOffset());
  }

  /** Returns {@code true} if the given CFI can be resolved against this document. */
  public boolean validateCfi(String cfi) {
    try {
      cfiToXPointer(cfi);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  /** Returns {@code true} if the given XPointer can be converted to a CFI. */
  public boolean validateXPointer(String xpointer) {
    try {
      xPointerToCfi(xpointer, null);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  // -- CFI to XPointer resolution ---------------------------------------------

  /** Walk the parsed CFI steps against the document and produce an XPointer string. */
  private String resolveToXPointer(List<CfiExpression.PathStep> steps, Integer charOffset) {
    if (!steps.isEmpty() && !steps.getLast().targetsElement()) {
      Object parent = walkSteps(steps.subList(0, steps.size() - 1));
      if (parent == null) {
        throw new IllegalArgumentException("Cannot resolve CFI text parent");
      }
      int step = steps.getLast().position();
      int offset = charOffset == null ? 0 : charOffset;
      var nodes = nav.getDirectTextNodes(parent);
      for (int i = 0; i < nodes.size(); i++) {
        var node = nodes.get(i);
        int localOffset = offset - node.offset();
        if (node.step() == step && localOffset >= 0 && localOffset <= node.text().length()) {
          int codePointOffset = node.text().codePointCount(0, localOffset);
          return elementToXPointer(parent) + "/text()[" + (i + 1) + "]." + codePointOffset;
        }
      }
      throw new IllegalArgumentException("Cannot resolve CFI text position");
    }

    Object target = walkSteps(steps);
    if (target == null) {
      throw new IllegalArgumentException("Cannot resolve CFI path to a document element");
    }

    if (charOffset != null) {
      return mapCharacterOffset(target, charOffset);
    }
    return elementToXPointer(target);
  }

  /**
   * Walk the CFI path steps against the document tree. Each even step position selects a child
   * element by its zero-based index (position / 2 - 1).
   */
  private Object walkSteps(List<CfiExpression.PathStep> steps) {
    Object node = nav.getBody();
    if (node == null) return null;

    for (var step : steps) {
      int idx = step.childElementIndex();
      List<Object> children = nav.getChildElements(node);
      if (idx < 0 || idx >= children.size()) {
        return node;
      }
      node = children.get(idx);
    }
    return node;
  }

  // -- XPointer to CFI construction -------------------------------------------

  /**
   * Decompose a KOReader XPointer into a DOM element and an optional text offset. The element is
   * located by interpreting the path segments against the document.
   */
  private XPointerParts decomposeXPointer(String xpointer) {
    Integer textOffset = null;
    String pathStr = xpointer;

    Integer textIndex = null;
    var suffix = TEXT_SUFFIX.matcher(xpointer);
    if (suffix.find()) {
      textOffset = Integer.parseInt(suffix.group(2));
      textIndex = suffix.group(1) == null ? null : Integer.parseInt(suffix.group(1));
      pathStr = xpointer.substring(0, suffix.start());
    } else if (xpointer.contains("/text()")) {
      throw new IllegalArgumentException("Invalid XPointer text position: " + xpointer);
    }

    Object element = locateXPointerElement(pathStr, textIndex != null);
    if (element == null) {
      throw new IllegalArgumentException("Cannot resolve XPointer path: " + pathStr);
    }
    if (textIndex != null) {
      var nodes = nav.getDirectTextNodes(element);
      if (textIndex < 1 || textIndex > nodes.size()) {
        throw new IllegalArgumentException("Text node " + textIndex + " not found");
      }
      var node = nodes.get(textIndex - 1);
      String text = node.text();
      if (textOffset > text.codePointCount(0, text.length())) {
        throw new IllegalArgumentException("Text offset exceeds text node length");
      }
      int utf16Offset = node.offset() + text.offsetByCodePoints(0, textOffset);
      return new XPointerParts(element, utf16Offset, node.step());
    }
    return new XPointerParts(element, textOffset, null);
  }

  /**
   * Resolve a KOReader XPointer element path to a DOM node.
   *
   * <p>Expects format: {@code /body/DocFragment[N]/body/tag[idx]/tag[idx]/...}
   *
   * <p>Indexed text locators use hierarchical child traversal. Element-only and unindexed-text
   * locators retain the legacy global lookup for an indexed last segment.
   */
  private Object locateXPointerElement(String path, boolean hierarchical) {
    int bodyStart = path.indexOf(XPOINTER_BODY_SUFFIX);
    if (bodyStart < 0) {
      throw new IllegalArgumentException("Invalid XPointer format (missing DocFragment): " + path);
    }
    String elementPath = path.substring(bodyStart + XPOINTER_BODY_SUFFIX.length());

    Object body = nav.getBody();
    if (body == null) {
      throw new IllegalArgumentException("Document has no body element");
    }
    if (elementPath.isEmpty()) {
      return body;
    }

    String[] segments =
        Arrays.stream(elementPath.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);

    if (segments.length == 0) {
      return body;
    }

    // Indexed text positions describe a child-by-child DOM path. Retain the legacy
    // global lookup only for older element/unindexed-text locators.
    if (hierarchical) return walkHierarchical(body, segments);

    // If the last segment is indexed (e.g. "p[54]"), look it up globally
    String lastSeg = segments[segments.length - 1];
    TagRef lastRef = TagRef.parse(lastSeg);
    if (lastRef.index >= 0) {
      List<Object> allMatching = nav.getElementsByTag(body, lastRef.tag);
      if (lastRef.index < allMatching.size()) {
        return allMatching.get(lastRef.index);
      }
      throw new IllegalArgumentException(
          "Element "
              + lastRef.tag
              + "["
              + (lastRef.index + 1)
              + "] not found (document has "
              + allMatching.size()
              + ")");
    }

    return walkHierarchical(body, segments);
  }

  /** Walk segments as a child-by-child path through the DOM. */
  private Object walkHierarchical(Object root, String[] segments) {
    Object current = root;
    for (String seg : segments) {
      TagRef ref = TagRef.parse(seg);
      int targetIdx = Math.max(ref.index, 0);

      int matchCount = 0;
      Object found = null;
      for (Object child : nav.getChildElements(current)) {
        if (nav.getTagName(child).equalsIgnoreCase(ref.tag)) {
          if (matchCount == targetIdx) {
            found = child;
            break;
          }
          matchCount++;
        }
      }
      if (found == null) {
        throw new IllegalArgumentException(
            "Cannot find child "
                + ref.tag
                + "["
                + (targetIdx + 1)
                + "] (found "
                + matchCount
                + " matching children)");
      }
      current = found;
    }
    return current;
  }

  // -- DOM / path mapping -----------------------------------------------------

  /**
   * Build CFI content-path steps by walking from the given element up to (but not including) the
   * body element, prepending {@code /4} for the body step (the HTML body is always the 2nd child of
   * {@code <html>}, so step = 2 * 2 = 4).
   */
  private List<CfiExpression.PathStep> domToCfiSteps(Object element) {
    List<CfiExpression.PathStep> steps = new ArrayList<>();
    Object cur = element;

    while (cur != null && !"body".equalsIgnoreCase(nav.getTagName(cur))) {
      Object parent = nav.getParent(cur);
      if (parent == null) break;

      int ordinal = 0;
      for (Object sibling : nav.getChildElements(parent)) {
        ordinal++;
        if (sibling == cur) break;
      }
      steps.addFirst(new CfiExpression.PathStep(ordinal * 2, null));
      cur = parent;
    }

    // Prepend /4 for the <body> step
    steps.addFirst(new CfiExpression.PathStep(4, null));
    return Collections.unmodifiableList(steps);
  }

  /**
   * Build a KOReader XPointer path string from a DOM element, walking upward to the body. Produces
   * indexed segments like {@code section[2]/div/p[3]} when there are multiple same-tag siblings,
   * and unindexed segments when the tag is unique among siblings.
   */
  private String elementToXPointer(Object target) {
    List<String> segments = new ArrayList<>();
    Object cur = target;
    Object body = nav.getBody();
    Object htmlRoot = body != null ? nav.getParent(body) : null;

    while (cur != null && cur != htmlRoot) {
      Object parent = nav.getParent(cur);
      if (parent == null) break;

      String tag = nav.getTagName(cur).toLowerCase();
      int sameTagBefore = 0;
      int sameTagTotal = 0;

      for (Object sibling : nav.getChildElements(parent)) {
        if (nav.getTagName(sibling).equalsIgnoreCase(tag)) {
          if (sibling == cur) sameTagBefore = sameTagTotal;
          sameTagTotal++;
        }
      }

      segments.addFirst(sameTagTotal == 1 ? tag : tag + "[" + (sameTagBefore + 1) + "]");
      cur = parent;
    }

    if (!segments.isEmpty() && segments.getFirst().startsWith("body")) {
      segments.removeFirst();
    }

    var sb = new StringBuilder(XPOINTER_PREFIX).append(spineIdx + 1).append(XPOINTER_BODY_SUFFIX);
    if (!segments.isEmpty()) {
      sb.append('/').append(String.join("/", segments));
    }
    return sb.toString();
  }

  // -- Text-offset mapping ----------------------------------------------------

  /**
   * Map a CFI character offset to a KOReader XPointer with a {@code /text().N} suffix. Walks the
   * text nodes of the element to find where the offset falls, then ascends to the nearest
   * significant (non-inline) ancestor.
   */
  private String mapCharacterOffset(Object element, int offset) {
    List<String> textFragments = nav.collectTextContent(element);

    int accumulated = 0;
    int localOffset = 0;
    boolean located = false;

    for (String fragment : textFragments) {
      if (accumulated + fragment.length() >= offset) {
        localOffset = offset - accumulated;
        located = true;
        break;
      }
      accumulated += fragment.length();
    }

    if (!located) {
      return elementToXPointer(element);
    }

    Object block = element;
    while (block != null && INLINE_TAGS.contains(nav.getTagName(block).toLowerCase())) {
      Object parent = nav.getParent(block);
      if (parent == null) break;
      block = parent;
    }

    return elementToXPointer(block != null ? block : element) + "/text()." + localOffset;
  }

  // -- Spine helpers ----------------------------------------------------------

  private String wrapCfi(String contentPath) {
    int spineStep = CfiExpression.spinePositionOf(spineIdx);
    return "epubcfi(/6/" + spineStep + "!" + contentPath + ")";
  }

  /**
   * Parse the one-based DocFragment index out of a KOReader XPointer and return the zero-based
   * index.
   */
  private static int parseDocFragmentIndex(String xpointer) {
    int open = xpointer.indexOf('[', XPOINTER_PREFIX.length() - 1);
    int close = xpointer.indexOf(']', open);
    if (open < 0 || close < 0) {
      throw new IllegalArgumentException("Cannot parse DocFragment index from: " + xpointer);
    }
    return Integer.parseInt(xpointer.substring(open + 1, close)) - 1;
  }

  // -- Value types ------------------------------------------------------------

  private record XPointerParts(Object element, Integer textOffset, Integer textStep) {}

  /**
   * Parsed tag reference from an XPointer segment, e.g. {@code "p[3]"} gives tag="p", index=2
   * (zero-based).
   */
  private record TagRef(String tag, int index) {

    /** Parse a segment like "p[3]" or "div". Returns index = -1 for unindexed segments. */
    static TagRef parse(String segment) {
      int bracket = segment.indexOf('[');
      if (bracket >= 0) {
        String tagName = segment.substring(0, bracket);
        int close = segment.indexOf(']', bracket);
        String numStr = segment.substring(bracket + 1, close >= 0 ? close : segment.length());
        return new TagRef(tagName, Integer.parseInt(numStr) - 1);
      }
      return new TagRef(segment, -1);
    }
  }
}
