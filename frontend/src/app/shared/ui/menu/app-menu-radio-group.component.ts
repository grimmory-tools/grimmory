import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';

@Component({
  selector: 'app-menu-radio-group',
  standalone: true,
  host: { role: 'group', class: 'contents', '[attr.aria-label]': 'ariaLabel() || null' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<ng-content />`,
})
export class AppMenuRadioGroupComponent<T> {
  readonly value = model<T | null>(null);
  readonly ariaLabel = input('');

  readonly valueSelected = output<T>();

  isSelected(candidate: T): boolean {
    return Object.is(this.value(), candidate);
  }

  select(candidate: T): void {
    this.value.set(candidate);
    this.valueSelected.emit(candidate);
  }
}
