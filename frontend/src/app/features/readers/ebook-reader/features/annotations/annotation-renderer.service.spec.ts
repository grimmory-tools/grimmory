import {firstValueFrom} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {AnnotationStyle} from '../../../../../shared/service/annotation.service';
import {Annotation, ReaderAnnotationService} from './annotation-renderer.service';

describe('ReaderAnnotationService', () => {
  let service: ReaderAnnotationService;

  const asRectList = (
    rects: { left: number; top?: number; bottom?: number; width: number; height?: number }[]
  ): DOMRectList => rects as unknown as DOMRectList;

  beforeEach(() => {
    service = new ReaderAnnotationService();
  });

  it('adds annotations lazily and replaces existing entries without duplication', async () => {
    const view = {
      addAnnotation: vi.fn().mockResolvedValue({index: 4, label: 'Annotation 4'}),
      deleteAnnotation: vi.fn().mockResolvedValue(undefined),
      showAnnotation: vi.fn().mockResolvedValue(undefined),
    };

    const initialAnnotation: Annotation = {value: 'epubcfi(/6/2[first]!/4/1:0)'};
    const observable = service.addAnnotation(view, initialAnnotation);

    expect(view.addAnnotation).not.toHaveBeenCalled();

    await expect(firstValueFrom(observable)).resolves.toEqual({index: 4, label: 'Annotation 4'});
    expect(view.addAnnotation).toHaveBeenCalledWith({value: initialAnnotation.value});
    expect(service.getAnnotationStyle(initialAnnotation.value)).toEqual({
      color: '#FACC15',
      style: 'highlight',
    });
    expect(service.getAllAnnotations()).toEqual([initialAnnotation]);

    const updatedAnnotation: Annotation = {
      value: initialAnnotation.value,
      color: '#2563eb',
      style: 'underline',
    };

    await firstValueFrom(service.addAnnotation(view, updatedAnnotation));

    expect(service.getAnnotationStyle(initialAnnotation.value)).toEqual({
      color: '#2563eb',
      style: 'underline',
    });
    expect(service.getAllAnnotations()).toEqual([updatedAnnotation]);
  });

  it('returns undefined and leaves caches untouched when addAnnotation has no view', async () => {
    const annotation: Annotation = {
      value: 'epubcfi(/6/4!/2/4:10)',
      color: '#f97316',
      style: 'strikethrough',
    };

    await expect(firstValueFrom(service.addAnnotation(undefined, annotation))).resolves.toBeUndefined();

    expect(service.getAnnotationStyle(annotation.value)).toBeUndefined();
    expect(service.getAllAnnotations()).toEqual([]);
  });

  it('deletes annotations only when a view exists and showAnnotation stays lazy', async () => {
    service.addAnnotations(undefined, [{
      value: 'epubcfi(/6/8!/4/2:0)',
      color: '#16a34a',
      style: 'squiggly',
    }]);

    await expect(firstValueFrom(service.deleteAnnotation(undefined, 'epubcfi(/6/8!/4/2:0)'))).resolves.toBeUndefined();
    expect(service.getAnnotationStyle('epubcfi(/6/8!/4/2:0)')).toEqual({
      color: '#16a34a',
      style: 'squiggly',
    });

    const view = {
      addAnnotation: vi.fn().mockResolvedValue(undefined),
      deleteAnnotation: vi.fn().mockResolvedValue(undefined),
      showAnnotation: vi.fn().mockResolvedValue(undefined),
    };

    const show$ = service.showAnnotation(view, 'epubcfi(/6/8!/4/2:0)');
    expect(view.showAnnotation).not.toHaveBeenCalled();

    await firstValueFrom(show$);
    expect(view.showAnnotation).toHaveBeenCalledWith({value: 'epubcfi(/6/8!/4/2:0)'});

    await firstValueFrom(service.deleteAnnotation(view, 'epubcfi(/6/8!/4/2:0)'));
    expect(view.deleteAnnotation).toHaveBeenCalledWith({value: 'epubcfi(/6/8!/4/2:0)'});
    expect(service.getAnnotationStyle('epubcfi(/6/8!/4/2:0)')).toBeUndefined();
    expect(service.getAllAnnotations()).toEqual([]);
  });

  it('adds batches with default styles, replaces duplicates, and tolerates missing views', () => {
    const view = {
      addAnnotation: vi.fn(),
      deleteAnnotation: vi.fn().mockResolvedValue(undefined),
      showAnnotation: vi.fn().mockResolvedValue(undefined),
    };

    service.addAnnotations(view, [
      {value: 'epubcfi(/6/2!/4/2:0)'},
      {value: 'epubcfi(/6/2!/4/4:0)', color: '#dc2626', style: 'underline'},
    ]);

    service.addAnnotations(undefined, [
      {value: 'epubcfi(/6/2!/4/2:0)', color: '#0891b2', style: 'strikethrough'},
    ]);

    expect(view.addAnnotation).toHaveBeenNthCalledWith(1, {value: 'epubcfi(/6/2!/4/2:0)'});
    expect(view.addAnnotation).toHaveBeenNthCalledWith(2, {value: 'epubcfi(/6/2!/4/4:0)'});
    expect(service.getAnnotationStyle('epubcfi(/6/2!/4/2:0)')).toEqual({
      color: '#0891b2',
      style: 'strikethrough',
    });
    expect(service.getAnnotationStyle('epubcfi(/6/2!/4/4:0)')).toEqual({
      color: '#dc2626',
      style: 'underline',
    });
    expect(service.getAllAnnotations()).toEqual([
      {value: 'epubcfi(/6/2!/4/2:0)', color: '#0891b2', style: 'strikethrough'},
      {value: 'epubcfi(/6/2!/4/4:0)', color: '#dc2626', style: 'underline'},
    ]);
  });

  it('builds underline and strikethrough overlay groups with expected geometry', () => {
    const underline = service.getOverlayerDrawFunction('underline') as unknown as (
      rects: DOMRectList,
      options: {color?: string; width?: number}
    ) => SVGElement;
    const underlineGroup = underline(asRectList([{left: 10, bottom: 24, width: 80}]), {
      color: '#1d4ed8',
      width: 3,
    });

    expect(underlineGroup.nodeName).toBe('g');
    expect(underlineGroup.getAttribute('fill')).toBe('#1d4ed8');
    expect(underlineGroup.children).toHaveLength(1);
    expect(underlineGroup.children[0].getAttribute('x')).toBe('10');
    expect(underlineGroup.children[0].getAttribute('y')).toBe('21');
    expect(underlineGroup.children[0].getAttribute('height')).toBe('3');
    expect(underlineGroup.children[0].getAttribute('width')).toBe('80');

    const strike = service.getOverlayerDrawFunction('strikethrough') as unknown as (
      rects: DOMRectList,
      options: {color?: string; width?: number}
    ) => SVGElement;
    const strikeGroup = strike(asRectList([{left: 6, top: 12, bottom: 20, width: 44}]), {
      color: '#dc2626',
      width: 4,
    });

    expect(strikeGroup.getAttribute('fill')).toBe('#dc2626');
    expect(strikeGroup.children).toHaveLength(1);
    expect(strikeGroup.children[0].getAttribute('y')).toBe('16');
    expect(strikeGroup.children[0].getAttribute('height')).toBe('4');
  });

  it('builds squiggly and highlight overlay groups with defaults and fallback styles', () => {
    const squiggly = service.getOverlayerDrawFunction('squiggly') as unknown as (
      rects: DOMRectList,
      options: {color?: string; width?: number}
    ) => SVGElement;
    const squigglyGroup = squiggly(asRectList([{left: 1, bottom: 8, width: 18}]), {
      color: '#7c3aed',
      width: 3,
    });

    expect(squigglyGroup.getAttribute('fill')).toBe('none');
    expect(squigglyGroup.getAttribute('stroke')).toBe('#7c3aed');
    expect(squigglyGroup.getAttribute('stroke-width')).toBe('3');
    expect(squigglyGroup.children).toHaveLength(1);
    expect(squigglyGroup.children[0].getAttribute('d')).toBe('M1 8l6 -4.5l6 4.5l6 -4.5');

    const highlight = service.getOverlayerDrawFunction('highlight');
    const highlightGroup = highlight(asRectList([{left: 4, top: 5, width: 30, height: 12}]), {
      color: '#fde047',
    });

    expect(highlightGroup.getAttribute('fill')).toBe('#fde047');
    expect(highlightGroup.style.opacity).toBe('var(--overlayer-highlight-opacity, .3)');
    expect(highlightGroup.style.mixBlendMode).toBe('var(--overlayer-highlight-blend-mode, multiply)');
    expect(highlightGroup.children[0].getAttribute('x')).toBe('4');
    expect(highlightGroup.children[0].getAttribute('height')).toBe('12');

    const fallback = service.getOverlayerDrawFunction('unsupported' as AnnotationStyle);
    const fallbackGroup = fallback(asRectList([{left: 2, top: 3, width: 9, height: 6}]), {});

    expect(fallbackGroup.getAttribute('fill')).toBe('yellow');
  });

  it('resets all cached annotations and styles', () => {
    service.addAnnotations(undefined, [
      {value: 'epubcfi(/6/10!/4/2:0)', color: '#22c55e', style: 'highlight'},
      {value: 'epubcfi(/6/10!/4/6:0)', color: '#f59e0b', style: 'underline'},
    ]);

    service.resetAnnotations();

    expect(service.getAllAnnotations()).toEqual([]);
    expect(service.getAnnotationStyle('epubcfi(/6/10!/4/2:0)')).toBeUndefined();
    expect(service.getAnnotationStyle('epubcfi(/6/10!/4/6:0)')).toBeUndefined();
  });
});
