import {
  Directive,
  ElementRef,
  effect,
  inject,
  Injectable,
  input,
  Renderer2,
  Signal,
  signal,
  WritableSignal,
} from '@angular/core';
import DOMPurify from 'dompurify';

@Injectable({providedIn: 'root'})
export class LucideIconService {
  private readonly cache = new Map<string, WritableSignal<SVGSVGElement | null>>();

  icon(name: string): Signal<SVGSVGElement | null> {
    const cached = this.cache.get(name);
    if (cached) {
      return cached;
    }

    const result = signal<SVGSVGElement | null>(null);
    this.cache.set(name, result);

    fetch(`assets/lucide/${encodeURIComponent(name)}.svg`)
      .then(response => {
        if (!response.ok) {
          return '';
        }

        return response.text();
      })
      .then(text => result.set(this.parseSvg(text)))
      .catch(() => result.set(null));

    return result;
  }

  private parseSvg(svgText: string): SVGSVGElement | null {
    if (!svgText) {
      return null;
    }

    const sanitizedSvgText = DOMPurify.sanitize(svgText, {USE_PROFILES: {svg: true}});
    const parsed = new DOMParser().parseFromString(sanitizedSvgText, 'image/svg+xml');
    if (parsed.querySelector('parsererror')) {
      return null;
    }

    const svg = parsed.querySelector('svg');
    return svg instanceof SVGSVGElement ? svg : null;
  }
}

@Directive({
  selector: 'svg[appIcon]',
  standalone: true,
})
export class AppIconDirective {
  readonly appIcon = input.required<string>();

  private readonly svg = inject<ElementRef<SVGSVGElement>>(ElementRef).nativeElement;
  private readonly icons = inject(LucideIconService);
  private readonly renderer = inject(Renderer2);

  constructor() {
    this.renderer.setAttribute(this.svg, 'viewBox', '0 0 24 24');
    this.renderer.setAttribute(this.svg, 'width', '1em');
    this.renderer.setAttribute(this.svg, 'height', '1em');
    this.renderer.setAttribute(this.svg, 'fill', 'none');
    this.renderer.setAttribute(this.svg, 'stroke', 'currentColor');
    this.renderer.setAttribute(this.svg, 'stroke-width', '2');
    this.renderer.setAttribute(this.svg, 'stroke-linecap', 'round');
    this.renderer.setAttribute(this.svg, 'stroke-linejoin', 'round');
    this.renderer.setAttribute(this.svg, 'aria-hidden', 'true');

    effect(() => {
      const icon = this.icons.icon(this.appIcon())();
      while (this.svg.firstChild) {
        this.renderer.removeChild(this.svg, this.svg.firstChild);
      }
      for (const child of Array.from(icon?.childNodes ?? [])) {
        this.renderer.appendChild(this.svg, document.importNode(child, true));
      }
    });
  }
}
