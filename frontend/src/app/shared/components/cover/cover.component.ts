import {ChangeDetectionStrategy, Component, input, computed} from '@angular/core';
import {Image} from 'primeng/image';

const COVER_COLORS = [
  '#1a1a2e', '#2d3436', '#0c3547', '#1e3d59', '#2c2c54', '#1b262c',
  '#2B2D42', '#3D405B', '#463F3A', '#1B2838', '#2E4057', '#4A3728',
];

function hashString(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) - hash) + str.charCodeAt(i);
    hash = hash & hash;
  }
  return Math.abs(hash);
}

export function coverColorFor(title: string | null | undefined, authors: string[] | null | undefined): string {
  title = title ?? ''
  const author = (authors ?? []).join(", ")

  return COVER_COLORS[hashString(title + author) % COVER_COLORS.length];
}

export type CoverPlaceholderSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-cover',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cover.component.html',
  styleUrl: './cover.component.scss',
  host: {
    '[style.width]': 'width()',
    '[style.height]': 'height()',
    '[class.is-square]': 'isSquare()',
  },
  imports: [
    Image,
  ]
})
export class CoverComponent {
  class = input<string | null | undefined>('');
  title = input<string | null | undefined>('');
  authors = input<string[] | null | undefined>([]);
  size = input<CoverPlaceholderSize>('md');
  src = input<string | null | undefined>('');
  isSquare = input<boolean>(false);
  width = input<string | undefined>();
  height = input<string | undefined>();
  preview = input<boolean>(false);

  color = computed(() => coverColorFor(this.title(), this.authors()));
}
