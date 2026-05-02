import {ChangeDetectionStrategy, Component, computed, input} from '@angular/core';

@Component({
  selector: 'app-rating',
  standalone: true,
  template: `
    <span
      class="app-rating"
      [style.--app-rating-color]="ratingColor()"
      [style.--app-rating-size]="size()"
      [attr.aria-label]="ratingAriaLabel()"
      role="img">
      @for (fill of starFills(); track $index) {
        <span class="app-rating-star" [style.--app-rating-star-fill]="fill">
          <svg class="app-rating-star-empty" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" aria-hidden="true" focusable="false">
            <path stroke-linecap="round" stroke-linejoin="round" d="M11.48 3.499a.562.562 0 0 1 1.04 0l2.125 5.111a.563.563 0 0 0 .475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 0 0-.182.557l1.285 5.385a.562.562 0 0 1-.84.61l-4.725-2.885a.562.562 0 0 0-.586 0L6.982 20.54a.562.562 0 0 1-.84-.61l1.285-5.386a.562.562 0 0 0-.182-.557l-4.204-3.602a.562.562 0 0 1 .321-.988l5.518-.442a.563.563 0 0 0 .475-.345L11.48 3.5Z" />
          </svg>
          <svg class="app-rating-star-filled" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <path fill-rule="evenodd" d="M10.788 3.21c.448-1.077 1.976-1.077 2.424 0l2.082 5.006 5.404.434c1.164.093 1.636 1.545.749 2.305l-4.117 3.527 1.257 5.273c.271 1.136-.964 2.033-1.96 1.425L12 18.354 7.373 21.18c-.996.608-2.231-.29-1.96-1.425l1.257-5.273-4.117-3.527c-.887-.76-.415-2.212.749-2.305l5.404-.434 2.082-5.005Z" clip-rule="evenodd" />
          </svg>
        </span>
      }
    </span>
  `,
  styleUrls: ['./rating.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RatingComponent {
  value = input<number | null>(null);
  color = input<string | null>(null);
  max = input(5);
  size = input('18px');
  ariaLabel = input<string | null>(null);

  protected readonly ratingColor = computed(() => this.color() ?? this.getRatingColor(this.value()));

  protected readonly ratingAriaLabel = computed(() => {
    const label = this.ariaLabel();
    if (label) return label;

    const rating = this.value();
    return rating === null ? null : `${rating.toFixed(1)} / ${this.max()}`;
  });

  protected readonly starFills = computed(() => {
    const rating = this.value();
    return Array.from({length: this.max()}, (_, index) => this.getStarFill(rating, index));
  });

  private getStarFill(rating: number | null, starIndex: number): string {
    if (rating === null) return '0%';
    const fill = Math.max(0, Math.min(1, rating - starIndex));
    return `${Math.round(fill * 100)}%`;
  }

  private getRatingColor(rating: number | null): string | null {
    if (rating === null) return null;
    if (rating >= 4.5) return 'rgb(34, 197, 94)';
    if (rating >= 4) return 'rgb(52, 211, 153)';
    if (rating >= 3.5) return 'rgb(234, 179, 8)';
    if (rating >= 2.5) return 'rgb(249, 115, 22)';
    return 'rgb(239, 68, 68)';
  }
}
