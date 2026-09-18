package org.booklore.util.koreader;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents the result of a CFI to XPointer conversion. Contains the XPointer string and optional
 * start/end positions for range-based references.
 */
public class XPointerResult implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Getter
  private final String xpointer;

  @Getter
  private final String pos0;

  @Getter
  private final String pos1;

  public XPointerResult(String xpointer, String pos0, String pos1) {
    this.xpointer = xpointer;
    this.pos0 = pos0;
    this.pos1 = pos1;
  }

  public XPointerResult(String xpointer) {
    this(xpointer, null, null);
  }

  @Override
  public String toString() {
    return xpointer;
  }
}
