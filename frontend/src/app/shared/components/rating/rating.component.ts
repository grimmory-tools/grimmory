import {ChangeDetectionStrategy, Component, computed, input} from '@angular/core';

@Component({
  selector: 'app-rating',
  standalone: true,
  templateUrl: './rating.component.html',
  styleUrls: ['./rating.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RatingComponent {
  value = input<number | null>(null);
  color = input<string | null>(null);
  max = input(5);
  size = input('18px');
  ariaLabel = input<string | null>(null);

  protected readonly ratingClass = computed(() => this.getRatingClass(this.value()));

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

  private getRatingClass(rating: number | null): string {
    if (rating === null) return 'app-rating-unrated';
    if (rating >= 4.5) return 'app-rating-excellent';
    if (rating >= 4) return 'app-rating-good';
    if (rating >= 3.5) return 'app-rating-mixed';
    if (rating >= 2.5) return 'app-rating-low';
    return 'app-rating-poor';
  }
}
