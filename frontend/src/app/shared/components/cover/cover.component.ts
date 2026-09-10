import {afterNextRender, ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input, signal, viewChild} from '@angular/core';
import {Image} from '@openng/optimus-ui/image';

import {ArtworkRevealGroupDirective} from './artwork-reveal-group.directive';

const COVER_HUES = [20, 155, 185, 205, 235, 265, 290, 320, 350];

function hashString(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = 31 * hash + str.charCodeAt(i);
    hash |= 0;
  }
  return hash;
}

function coverHueFor(title: string, author: string): number {
  return COVER_HUES[Math.abs(hashString(title + author)) % COVER_HUES.length];
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
    class: '@container block w-full transition-opacity duration-200 ease-out motion-reduce:transition-none',
    '[class.opacity-0]': 'revealGroup?.ready() === false',
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

  protected readonly revealGroup = inject(ArtworkRevealGroupDirective, {optional: true});
  protected readonly markReady = this.revealGroup?.register() ?? (() => undefined);
  private readonly previewImage = viewChild(Image);
  private readonly failedSrc = signal<string | null | undefined>(null);

  protected readonly authorsLabel = computed(() => {
    const authors = this.authors();
    return Array.isArray(authors) ? authors.join(', ') : authors ?? '';
  });
  protected readonly hue = computed(() => coverHueFor(this.title() ?? '', this.authorsLabel()));
  protected readonly titleOnly = computed(
    () => !this.authorsLabel() || (hashString((this.title() ?? '') + this.authorsLabel()) & 1) === 0,
  );
  protected readonly imageClass = computed(() => [
    'cover-img block w-full rounded-[inherit]',
    this.natural() ? 'h-auto' : 'h-full',
    this.fit() === 'cover' ? 'object-cover' : 'object-contain',
  ].join(' '));

  protected readonly showImage = computed(() => !!this.src() && this.failedSrc() !== this.src());

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.markReady();
      this.closePreview();
    });
    afterNextRender(() => {
      if (!this.showImage() || this.preview()) {
        this.markReady();
      }
    });
  }

  protected closePreview(): void {
    if (!this.preview()) {
      return;
    }

    this.previewImage()?.closePreview();
  }

  protected onError(): void {
    this.failedSrc.set(this.src());
    this.markReady();
  }
}
