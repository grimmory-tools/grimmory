import type {AnnotationTransferItem} from '@embedpdf/snippet';

/**
 * Stored annotation wrapper format.
 * Legacy pdf.js annotations are raw arrays with `annotationType` keys.
 * New format wraps EmbedPDF AnnotationTransferItem[] in a versioned envelope.
 */
export interface StoredAnnotations {
  format: 'embedpdf';
  version: 1;
  annotations: AnnotationTransferItem[];
}

type AnnotationWithOpacity = AnnotationTransferItem['annotation'] & {
  type?: number;
  opacity?: number;
};

interface SerializableCtx {
  data?: ArrayBuffer | string;
  _dataEncoding?: 'base64';
  [key: string]: unknown;
}

interface SerializableAnnotationTransferItem {
  annotation?: AnnotationWithOpacity;
  ctx?: SerializableCtx;
  [key: string]: unknown;
}

function toSerializableAnnotationTransferItem(item: AnnotationTransferItem): SerializableAnnotationTransferItem {
  return item as unknown as SerializableAnnotationTransferItem;
}

function toAnnotationTransferItem(item: SerializableAnnotationTransferItem): AnnotationTransferItem {
  return item as AnnotationTransferItem;
}

/**
 * Detect and parse stored annotation data.
 * Returns EmbedPDF AnnotationTransferItems ready for import, or empty array.
 */
export function parseStoredAnnotations(jsonString: string | null | undefined): AnnotationTransferItem[] {
  if (!jsonString) return [];

  try {
    const parsed = JSON.parse(jsonString);

    // New format: { format: 'embedpdf', version: 1, annotations: [...] }
    if (parsed && parsed.format === 'embedpdf' && Array.isArray(parsed.annotations)) {
      const storedItems = parsed.annotations as SerializableAnnotationTransferItem[];
      return storedItems.reduce<AnnotationTransferItem[]>((acc, item) => {
        try {
          acc.push(restoreArrayBuffers(item));
        } catch {
          // Skip malformed entries, keep valid annotations
        }
        return acc;
      }, []);
    }

    // Legacy pdf.js format: raw array of editor annotations
    if (Array.isArray(parsed)) {
      return convertLegacyAnnotations(parsed);
    }

    return [];
  } catch {
    return [];
  }
}

/**
 * Wrap EmbedPDF annotations in the versioned envelope for storage.
 * Handles ArrayBuffer fields (e.g. stamp ctx.data) by encoding to base64.
 */
export function serializeAnnotations(items: AnnotationTransferItem[]): string {
  const serializable = items.map((item): SerializableAnnotationTransferItem => {
    const itemRecord = toSerializableAnnotationTransferItem(item);
    if (itemRecord.ctx?.data instanceof ArrayBuffer) {
      return {
        ...itemRecord,
        ctx: {
          ...itemRecord.ctx,
          data: arrayBufferToBase64(itemRecord.ctx.data),
          _dataEncoding: 'base64',
        },
      };
    }
    return itemRecord;
  });
  const wrapped = {
    format: 'embedpdf' as const,
    version: 1 as const,
    annotations: serializable,
  };
  return JSON.stringify(wrapped);
}

/**
 * Best-effort conversion of legacy pdf.js editor annotations.
 * These are the serialized annotations from the legacy ngx-extended-pdf-viewer's
 * getSerializedAnnotations() which outputs pdf.js editor format.
 *
 * This is a lossy conversion not all annotation types or properties
 * can be fully mapped. After the first save in the new format,
 * legacy data is overwritten.
 */
function convertLegacyAnnotations(legacyAnnotations: Record<string, unknown>[]): AnnotationTransferItem[] {
  const items: AnnotationTransferItem[] = [];

  for (const legacy of legacyAnnotations) {
    try {
      const item = convertSingleAnnotation(legacy);
      if (item) {
        items.push(item);
      }
    } catch {
      // Skip annotations that can't be converted
    }
  }

  return items;
}

function convertSingleAnnotation(legacy: Record<string, unknown>): AnnotationTransferItem | null {
  const annotationType = legacy['annotationType'] as number | undefined;

  // pdf.js annotation editor types:
  // 3 = freetext, 9 = highlight, 15 = ink
  switch (annotationType) {
    case 9: // highlight
      return convertHighlight(legacy);
    case 15: // ink
      return convertInk(legacy);
    case 3: // freetext
      return convertFreeText(legacy);
    default:
      return null;
  }
}

function convertHighlight(legacy: Record<string, unknown>): AnnotationTransferItem | null {
  const color = legacy['color'] as number[] | undefined;
  const pageIndex = (legacy['pageIndex'] as number) ?? 0;
  const rect = legacy['rect'] as number[] | undefined;

  if (!rect || rect.length < 4) return null;

  const hexColor = color ? rgbArrayToHex(color) : '#FFFF00';
  const x = rect[0];
  const y = rect[1];
  const w = rect[2] - rect[0];
  const h = rect[3] - rect[1];

  // Cap opacity for highlights to prevent solid blocks
  const rawOpacity = (legacy['opacity'] as number) ?? 0.4;
  const opacity = Math.min(rawOpacity, 0.6);

  return {
    annotation: {
      type: 9, // PdfAnnotationSubtype.HIGHLIGHT
      pageIndex,
      rect: {origin: {x, y}, size: {width: w, height: h}},
      segmentRects: [{origin: {x, y}, size: {width: w, height: h}}],
      color: hexColor,
      opacity,
      id: crypto.randomUUID(),
    } as never,
  };
}

function convertInk(legacy: Record<string, unknown>): AnnotationTransferItem | null {
  const color = legacy['color'] as number[] | undefined;
  const pageIndex = (legacy['pageIndex'] as number) ?? 0;
  const paths = legacy['paths'] as {bezier: number[]}[] | number[][][] | undefined;
  const thickness = (legacy['thickness'] as number) ?? 1;

  if (!paths) return null;

  const hexColor = color ? rgbArrayToHex(color) : '#000000';

  // pdf.js ink paths can be bezier arrays or point arrays
  const inkList: {points: {x: number; y: number}[]}[] = [];
  for (const path of paths) {
    if (Array.isArray(path) && Array.isArray(path[0])) {
      // Array of point pairs
      const points = (path as number[][]).map(p => ({x: p[0], y: p[1]}));
      inkList.push({points});
    } else if (typeof path === 'object' && 'bezier' in (path as Record<string, unknown>)) {
      // Bezier curve data - extract control points
      const bezier = (path as {bezier: number[]}).bezier;
      const points: {x: number; y: number}[] = [];
      for (let i = 0; i < bezier.length; i += 2) {
        points.push({x: bezier[i], y: bezier[i + 1]});
      }
      inkList.push({points});
    }
  }

  if (inkList.length === 0) return null;

  return {
    annotation: {
      type: 15, // PdfAnnotationSubtype.INK
      pageIndex,
      rect: {origin: {x: 0, y: 0}, size: {width: 0, height: 0}},
      color: hexColor,
      strokeWidth: thickness,
      opacity: 1,
      inkList,
      id: crypto.randomUUID(),
    } as never,
  };
}

function convertFreeText(legacy: Record<string, unknown>): AnnotationTransferItem | null {
  const pageIndex = (legacy['pageIndex'] as number) ?? 0;
  const rect = legacy['rect'] as number[] | undefined;
  const value = (legacy['value'] as string) ?? '';
  const fontSize = (legacy['fontSize'] as number) ?? 12;
  const color = legacy['color'] as number[] | undefined;

  if (!rect || rect.length < 4) return null;

  const x = rect[0];
  const y = rect[1];
  const w = rect[2] - rect[0];
  const h = rect[3] - rect[1];

  return {
    annotation: {
      type: 3, // PdfAnnotationSubtype.FREETEXT
      pageIndex,
      rect: {origin: {x, y}, size: {width: w, height: h}},
      contents: value,
      fontSize,
      fontColor: color ? rgbArrayToHex(color) : '#000000',
      id: crypto.randomUUID(),
    } as never,
  };
}

function rgbArrayToHex(rgb: number[]): string {
  const r = Math.round(Math.min(255, Math.max(0, rgb[0] * 255)));
  const g = Math.round(Math.min(255, Math.max(0, rgb[1] * 255)));
  const b = Math.round(Math.min(255, Math.max(0, rgb[2] * 255)));
  return `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}`.toUpperCase();
}

// --- ArrayBuffer <-> base64 helpers ---

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary);
}

function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

function restoreArrayBuffers(item: SerializableAnnotationTransferItem): AnnotationTransferItem {
  const next: SerializableAnnotationTransferItem = {
    ...item,
    annotation: item.annotation ? {...item.annotation} : undefined,
  };

  // Cap opacity for highlights to prevent solid blocks
  if (next.annotation?.type === 9) {
    const rawOpacity = next.annotation.opacity ?? 0.4;
    next.annotation.opacity = Math.min(rawOpacity, 0.6);
  }

  if (next.ctx?._dataEncoding === 'base64' && typeof next.ctx.data === 'string') {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const {_dataEncoding: _, ...rest} = next.ctx;
    return toAnnotationTransferItem({
      ...next,
      ctx: {
        ...rest,
        data: base64ToArrayBuffer(next.ctx.data),
      },
    });
  }
  return toAnnotationTransferItem(next);
}
