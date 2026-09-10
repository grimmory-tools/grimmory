import {Component, computed, input, output} from '@angular/core';
import {LucideSearch, LucideX} from '@lucide/angular';

import {AppInputComponent} from '../../ui/input/app-input.component';
import {type AppInputSize} from '../../ui/input/app-input.variants';

const CLEAR_BUTTON_CLASS =
  '-mr-1.5 inline-flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-md ' +
  'text-text-muted touch-manipulation transition-colors hover:text-text-strong ' +
  'focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary ' +
  'pointer-coarse:-mr-3 pointer-coarse:size-10';

@Component({
  selector: 'app-browse-search-input',
  imports: [AppInputComponent, LucideSearch, LucideX],
  host: {class: 'block w-full'},
  template: `
    <app-input
      [size]="size()"
      [placeholder]="placeholder()"
      [ariaLabel]="ariaLabel() || placeholder()"
      [value]="value()"
      (valueChange)="valueChange.emit($event)"
      (enterPressed)="entered.emit()">
      <svg lucideSearch appInputLeading [class]="iconClass()" aria-hidden="true"></svg>
      @if (value()) {
        <button
          type="button"
          appInputTrailing
          [class]="clearButtonClass"
          [attr.aria-label]="clearLabel()"
          (click)="cleared.emit()">
          <svg lucideX [class]="iconClass()" aria-hidden="true"></svg>
        </button>
      }
    </app-input>
  `,
})
export class BrowseSearchInputComponent {
  readonly value = input.required<string>();
  readonly placeholder = input('');
  readonly ariaLabel = input('');
  readonly clearLabel = input('');
  readonly size = input<AppInputSize>('md');

  readonly valueChange = output<string>();
  readonly cleared = output<void>();
  readonly entered = output<void>();

  protected readonly clearButtonClass = CLEAR_BUTTON_CLASS;
  protected readonly iconClass = computed(() => this.size() === 'sm' ? 'size-3.5' : 'size-4');
}
