import { ChangeDetectionStrategy, Component, input } from '@angular/core';

const CHIP_CLASS =
  'pointer-events-none w-fit max-w-xs select-none break-words rounded-md bg-surface-900 ' +
  'px-2 py-1 text-xs font-medium leading-snug text-surface-50 shadow-pop dark:bg-surface-700';

@Component({
  selector: 'app-tooltip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div role="tooltip" [id]="id()" [class]="chipClass">{{ text() }}</div>`,
})
export class AppTooltipContainerComponent {
  readonly id = input('');
  readonly text = input('');

  protected readonly chipClass = CHIP_CLASS;
}
