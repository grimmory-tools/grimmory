import {Directive, ElementRef, Renderer2, effect, inject, input} from '@angular/core';

@Directive({
  selector: '[appSvgContent]',
  standalone: true,
})
export class SvgContentDirective {
  readonly appSvgContent = input<string | null>(null);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly renderer = inject(Renderer2);

  constructor() {
    effect(() => {
      this.render(this.appSvgContent());
    });
  }

  private render(svgText: string | null): void {
    while (this.host.firstChild) {
      this.renderer.removeChild(this.host, this.host.firstChild);
    }

    if (!svgText) {
      return;
    }

    const parsed = new DOMParser().parseFromString(svgText, 'image/svg+xml');
    if (parsed.querySelector('parsererror')) {
      return;
    }

    const svg = parsed.querySelector('svg');
    if (!(svg instanceof SVGSVGElement)) {
      return;
    }

    this.renderer.appendChild(this.host, document.importNode(svg, true));
  }
}
