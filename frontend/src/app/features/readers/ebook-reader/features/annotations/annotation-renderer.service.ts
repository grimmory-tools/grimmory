import {Injectable} from '@angular/core';
import {defer, from, Observable, of} from 'rxjs';
import {AnnotationStyle} from '../../../../../shared/service/annotation.service';

export type {AnnotationStyle};

export interface Annotation {
  value: string;
  color?: string;
  style?: AnnotationStyle;
}

@Injectable({
  providedIn: 'root'
})
export class ReaderAnnotationService {
  private annotationStyles = new Map<string, { color: string; style: AnnotationStyle }>();
  private allAnnotations: Annotation[] = [];

  addAnnotation(view: any, annotation: Annotation): Observable<{ index: number; label: string } | undefined> {
    if (!view) return of(undefined);

    const style = annotation.style || 'highlight';
    const color = annotation.color || '#FACC15';

    this.annotationStyles.set(annotation.value, {color, style});

    const existingIndex = this.allAnnotations.findIndex(a => a.value === annotation.value);
    if (existingIndex >= 0) {
      this.allAnnotations[existingIndex] = annotation;
    } else {
      this.allAnnotations.push(annotation);
    }

    return defer(() => from(view.addAnnotation({value: annotation.value}) as Promise<{ index: number; label: string } | undefined>));
  }

  deleteAnnotation(view: any, cfi: string): Observable<void> {
    if (!view) return of(undefined);

    this.annotationStyles.delete(cfi);
    this.allAnnotations = this.allAnnotations.filter(a => a.value !== cfi);

    return defer(() => from(view.deleteAnnotation({value: cfi}) as Promise<void>));
  }

  showAnnotation(view: any, cfi: string): Observable<void> {
    if (!view) return of(undefined);

    return defer(() => from(view.showAnnotation({value: cfi}) as Promise<void>));
  }

  addAnnotations(view: any, annotations: Annotation[]): void {
    annotations.forEach(annotation => {
      const style = annotation.style || 'highlight';
      const color = annotation.color || '#FACC15';
      this.annotationStyles.set(annotation.value, {color, style});

      const existingIndex = this.allAnnotations.findIndex(a => a.value === annotation.value);
      if (existingIndex >= 0) {
        this.allAnnotations[existingIndex] = annotation;
      } else {
        this.allAnnotations.push(annotation);
      }

      view?.addAnnotation({value: annotation.value});
    });
  }

  getAnnotationStyle(cfi: string): { color: string; style: AnnotationStyle } | undefined {
    return this.annotationStyles.get(cfi);
  }

  getOverlayerDrawFunction(style: AnnotationStyle): (rects: DOMRectList, options: { color?: string }) => SVGElement {
    const createSVGElement = (tag: string): SVGElement =>
      document.createElementNS('http://www.w3.org/2000/svg', tag);

    switch (style) {
      case 'underline':
        return (rects: DOMRectList, options: { color?: string; width?: number } = {}) => {
          const {color = 'red', width: strokeWidth = 2} = options;
          const g = createSVGElement('g');
          g.setAttribute('fill', color);
          for (const {left, bottom, width} of Array.from(rects)) {
            const el = createSVGElement('rect');
            el.setAttribute('x', String(left));
            el.setAttribute('y', String(bottom - strokeWidth));
            el.setAttribute('height', String(strokeWidth));
            el.setAttribute('width', String(width));
            g.append(el);
          }
          return g;
        };

      case 'strikethrough':
        return (rects: DOMRectList, options: { color?: string; width?: number } = {}) => {
          const {color = 'red', width: strokeWidth = 2} = options;
          const g = createSVGElement('g');
          g.setAttribute('fill', color);
          for (const {left, top, bottom, width} of Array.from(rects)) {
            const el = createSVGElement('rect');
            el.setAttribute('x', String(left));
            el.setAttribute('y', String((top + bottom) / 2));
            el.setAttribute('height', String(strokeWidth));
            el.setAttribute('width', String(width));
            g.append(el);
          }
          return g;
        };

      case 'squiggly':
        return (rects: DOMRectList, options: { color?: string; width?: number } = {}) => {
          const {color = 'red', width: strokeWidth = 2} = options;
          const g = createSVGElement('g');
          g.setAttribute('fill', 'none');
          g.setAttribute('stroke', color);
          g.setAttribute('stroke-width', String(strokeWidth));
          const block = strokeWidth * 1.5;
          for (const {left, bottom, width} of Array.from(rects)) {
            const el = createSVGElement('path');
            const n = Math.round(width / block / 1.5);
            const inline = width / n;
            const ls = Array.from({length: n}, (_, i) =>
              `l${inline} ${i % 2 ? block : -block}`).join('');
            el.setAttribute('d', `M${left} ${bottom}${ls}`);
            g.append(el);
          }
          return g;
        };

      case 'highlight':
      default:
        return (rects: DOMRectList, options: { color?: string } = {}) => {
          const {color = 'yellow'} = options;
          const g = createSVGElement('g');
          g.setAttribute('fill', color);
          (g as SVGElement).style.opacity = 'var(--overlayer-highlight-opacity, .3)';
          (g as SVGElement).style.mixBlendMode = 'var(--overlayer-highlight-blend-mode, multiply)';
          for (const {left, top, height, width} of Array.from(rects)) {
            const el = createSVGElement('rect');
            el.setAttribute('x', String(left));
            el.setAttribute('y', String(top));
            el.setAttribute('height', String(height));
            el.setAttribute('width', String(width));
            g.append(el);
          }
          return g;
        };
    }
  }

  getAllAnnotations(): Annotation[] {
    return [...this.allAnnotations];
  }

  resetAnnotations(): void {
    this.annotationStyles.clear();
    this.allAnnotations = [];
  }
}
