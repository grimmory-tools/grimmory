import {describe, expect, it} from 'vitest';
import type {AnnotationTransferItem} from '@embedpdf/snippet';

import {parseStoredAnnotations, serializeAnnotations} from './annotation-converter';

function createHighlightItem(opacity = 0.9): AnnotationTransferItem {
  return {
    annotation: {
      type: 9,
      pageIndex: 0,
      id: 'annotation-1',
      rect: {origin: {x: 1, y: 2}, size: {width: 10, height: 12}},
      segmentRects: [{origin: {x: 1, y: 2}, size: {width: 10, height: 12}}],
      color: '#FFFF00',
      opacity,
    } as never,
    ctx: {
      data: new Uint8Array([1, 2, 3, 4]).buffer,
    },
  } as AnnotationTransferItem;
}

describe('annotation-converter', () => {
  it('serializes and restores ArrayBuffer payloads', () => {
    const source = [createHighlightItem(0.5)];

    const json = serializeAnnotations(source);
    const restored = parseStoredAnnotations(json);

    const first = restored[0] as {ctx?: {data?: ArrayBuffer}};
    expect(restored).toHaveLength(1);
    expect(first.ctx?.data).toBeInstanceOf(ArrayBuffer);

    const bytes = new Uint8Array(first.ctx?.data ?? new ArrayBuffer(0));
    expect(Array.from(bytes)).toEqual([1, 2, 3, 4]);
  });

  it('caps highlight opacity when reading stored embedpdf annotations', () => {
    const json = JSON.stringify({
      format: 'embedpdf',
      version: 1,
      annotations: [
        {
          ...createHighlightItem(0.95),
          ctx: {
            data: 'AQIDBA==',
            _dataEncoding: 'base64',
          },
        },
      ],
    });

    const restored = parseStoredAnnotations(json);
    const annotation = restored[0]?.annotation as {opacity?: number};
    expect(annotation.opacity).toBe(0.6);
  });

  it('converts legacy pdf.js annotations to embedpdf transfer items', () => {
    const legacy = JSON.stringify([
      {
        annotationType: 9,
        pageIndex: 3,
        rect: [10, 20, 80, 120],
        color: [1, 0.8, 0],
        opacity: 0.8,
      },
    ]);

    const converted = parseStoredAnnotations(legacy);
    const annotation = converted[0]?.annotation as {type?: number; pageIndex?: number; opacity?: number};

    expect(converted).toHaveLength(1);
    expect(annotation.type).toBe(9);
    expect(annotation.pageIndex).toBe(3);
    expect(annotation.opacity).toBe(0.6);
  });

  it('returns empty list for malformed payload', () => {
    expect(parseStoredAnnotations('{not-json')).toEqual([]);
  });
});
