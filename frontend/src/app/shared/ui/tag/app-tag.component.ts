import { booleanAttribute, ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { translateSignal } from '@jsverse/transloco';
import { cn } from '../cn';
import { tagRemoveVariants, tagVariants, type TagColor, type TagSize } from './app-tag.variants';

@Component({
  selector: 'app-tag',
  standalone: true,
  host: { class: 'inline-flex align-middle' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (clickable()) {
      <button
        type="button"
        [class]="tagClass()"
        [style.background-color]="customBg()"
        [style.color]="customTextColor()"
        [style.border-color]="customBorder()"
        [attr.aria-label]="ariaLabel() || null"
        (click)="clicked.emit($event)">
        @if (icon()) {
          <i [class]="icon()" aria-hidden="true"></i>
        }
        @if (label()) {
          <span>{{ label() }}</span>
        }
        <ng-content />
      </button>
    } @else {
      <span
        [class]="tagClass()"
        [style.background-color]="customBg()"
        [style.color]="customTextColor()"
        [style.border-color]="customBorder()">
        @if (icon()) {
          <i [class]="icon()" aria-hidden="true"></i>
        }
        @if (label()) {
          <span>{{ label() }}</span>
        }
        <ng-content />
        @if (removable()) {
          <button
            type="button"
            [class]="removeClass()"
            [attr.aria-label]="resolvedRemoveLabel()"
            (click)="$event.stopPropagation(); remove.emit($event)">
            <i class="pi pi-times text-[0.7em]" aria-hidden="true"></i>
          </button>
        }
      </span>
    }
  `,
})
export class AppTagComponent {
  readonly color = input<TagColor>('neutral');
  readonly size = input<TagSize>('md');
  readonly label = input('');
  readonly icon = input('');
  readonly styleClass = input('');
  readonly customColor = input('');
  readonly clickable = input(false, { transform: booleanAttribute });
  readonly removable = input(false, { transform: booleanAttribute });
  readonly ariaLabel = input('');
  readonly removeLabel = input('');

  readonly clicked = output<MouseEvent>();
  readonly remove = output<MouseEvent>();

  protected readonly tagClass = computed(() =>
    cn(
      tagVariants({
        color: this.customTextColor() ? 'custom' : this.color(),
        size: this.size(),
        clickable: this.clickable(),
      }),
      this.styleClass(),
    ),
  );

  protected readonly removeClass = computed(() => tagRemoveVariants({ size: this.size() }));
  private readonly removeLabelParams = computed(() => ({ label: this.label() }));
  private readonly removeTagLabel = translateSignal('shared.ui.tag.removeTag');
  private readonly removeLabelWithValue = translateSignal('shared.ui.tag.removeLabel', this.removeLabelParams);
  protected readonly resolvedRemoveLabel = computed(
    () => this.removeLabel() || (this.label() ? this.removeLabelWithValue() : this.removeTagLabel()),
  );
  protected readonly customTextColor = computed(() => this.customColor() || null);

  protected readonly customBg = computed(() => {
    const color = this.customTextColor();
    return color ? `color-mix(in srgb, ${color}, transparent 90%)` : null;
  });
  protected readonly customBorder = computed(() => {
    const color = this.customTextColor();
    return color ? `color-mix(in srgb, ${color}, transparent 85%)` : null;
  });
}
