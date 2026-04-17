import {ChangeDetectionStrategy, Component, computed, input} from '@angular/core';

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

export function coverColorFor(title: string, author: string): string {
  return COVER_COLORS[hashString(title + author) % COVER_COLORS.length];
}

export type CoverPlaceholderSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-cover-placeholder',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="placeholder"
      [class.size-sm]="size() === 'sm'"
      [class.size-md]="size() === 'md'"
      [class.size-lg]="size() === 'lg'"
      [style.background]="color()"
    >
      <div class="placeholder-title">{{ title() }}</div>
      <div class="placeholder-author">{{ author() }}</div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      height: 100%;
      container-type: inline-size;
    }

    .placeholder {
      width: 100%;
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      text-align: center;
      border-radius: inherit;
      overflow: hidden;
    }

    .placeholder.size-sm { padding: min(0.5rem, 4cqi); }
    .placeholder.size-md { padding: min(1rem, 6cqi); }
    .placeholder.size-lg { padding: min(1.5rem, 8cqi); }

    .placeholder-title {
      color: rgba(255, 255, 255, 0.9);
      font-weight: 600;
      line-height: 1.375;
      display: -webkit-box;
      -webkit-line-clamp: 3;
      -webkit-box-orient: vertical;
      overflow: hidden;
      word-break: break-word;
      max-width: 100%;
    }

    .size-sm .placeholder-title {
      font-size: min(11px, 10cqi);
      margin-bottom: 2px;
    }

    .size-md .placeholder-title {
      font-size: min(13px, 10cqi);
      margin-bottom: 4px;
    }

    .size-lg .placeholder-title {
      font-size: min(15px, 10cqi);
      margin-bottom: 8px;
    }

    .placeholder-author {
      color: rgba(255, 255, 255, 0.5);
      letter-spacing: 0.025em;
      display: -webkit-box;
      -webkit-line-clamp: 1;
      -webkit-box-orient: vertical;
      overflow: hidden;
      word-break: break-word;
      max-width: 100%;
    }

    .size-sm .placeholder-author { font-size: min(9px, 8cqi); }
    .size-md .placeholder-author { font-size: min(11px, 8cqi); }
    .size-lg .placeholder-author { font-size: min(12px, 8cqi); }
  `]
})
export class CoverPlaceholderComponent {
  title = input('');
  author = input('');
  size = input<CoverPlaceholderSize>('md');

  protected color = computed(() => coverColorFor(this.title(), this.author()));
}
