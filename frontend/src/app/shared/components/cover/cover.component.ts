import {ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input, signal, viewChild} from '@angular/core';
import {Image} from 'primeng/image';

const COVER_COLORS = [
  '#1a1a2e', '#2d3436', '#0c3547', '#1e3d59', '#2c2c54', '#1b262c',
  '#2B2D42', '#3D405B', '#463F3A', '#1B2838', '#2E4057', '#4A3728',
];

function hashString(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = 31 * hash + str.charCodeAt(i);
    hash |= 0;
  }
  return hash;
}

function coverColorFor(title: string, author: string): string {
  return COVER_COLORS[Math.abs(hashString(title + author) % COVER_COLORS.length)];
}

type CoverSize = 'sm' | 'md' | 'lg';
type CoverFit = 'cover' | 'contain';
type CoverAuthors = string | string[];

@Component({
  selector: 'app-cover',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Image],
  templateUrl: './cover.component.html',
  host: {
    class: 'block w-full',
    '[class.h-full]': '!natural()',
    '[class.h-auto]': 'natural()',
    '(window:popstate)': 'closePreview()',
  },
})
export class CoverComponent {
  readonly src = input<string | null | undefined>(null);
  readonly title = input<string | null | undefined>('');
  readonly authors = input<CoverAuthors | null | undefined>([]);
  readonly size = input<CoverSize>('md');
  readonly fit = input<CoverFit>('cover');
  readonly loading = input<'eager' | 'lazy'>('eager');
  readonly alt = input('');
  readonly natural = input(false);
  readonly preview = input(false);

  private readonly previewImage = viewChild(Image);
  private readonly failedSrc = signal<string | null | undefined>(null);

  protected readonly authorsLabel = computed(() => {
    const authors = this.authors();
    return Array.isArray(authors) ? authors.join(', ') : authors ?? '';
  });
  protected readonly color = computed(() => coverColorFor(this.title() ?? '', this.authorsLabel()));
  protected readonly imageClass = computed(() => [
    'cover-img block w-full rounded-[inherit]',
    this.natural() ? 'h-auto' : 'h-full',
    this.fit() === 'cover' ? 'object-cover' : 'object-contain',
  ].join(' '));

  protected readonly showImage = computed(() => !!this.src() && this.failedSrc() !== this.src());

  constructor() {
    inject(DestroyRef).onDestroy(() => this.closePreview());
  }

  protected closePreview(): void {
    if (!this.preview()) {
      return;
    }

    this.previewImage()?.closePreview();
  }

  protected onError(): void {
    this.failedSrc.set(this.src());
  }
}
