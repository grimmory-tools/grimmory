import {booleanAttribute, ChangeDetectionStrategy, Component, computed, input, output} from '@angular/core';
import {LucideCheck} from '@lucide/angular';

import {cn} from '../../../../shared/ui/cn';

const cardCheckboxBaseClass =
  'group/check flex size-6 items-center justify-center rounded-md bg-black/80 text-white ring-1 ring-inset ring-white/30 transition-opacity motion-reduce:transition-none ' +
  'pointer-events-none opacity-0 ' +
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary';
const cardCheckboxInteractiveClass =
  'group-hover/card:pointer-events-auto group-hover/card:opacity-100 group-has-[:focus-visible]/card:pointer-events-auto group-has-[:focus-visible]/card:opacity-100 group-data-[active=true]/card:pointer-events-auto group-data-[active=true]/card:opacity-100';
const cardCheckboxSelectedClass = 'bg-primary text-primary-contrast ring-primary';
const cardCheckIconClass =
  'size-3.5 opacity-0 transition-opacity group-hover/check:opacity-100 motion-reduce:transition-none';

@Component({
  selector: 'app-card-selection',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideCheck],
  host: {class: 'pointer-events-none absolute left-2 top-2 z-20'},
  template: `
    @if (selectionMode()) {
      <span [class]="controlClass()" aria-hidden="true">
        <svg lucideCheck [class]="checkClass()"></svg>
      </span>
    } @else {
      <button
        type="button"
        [class]="controlClass()"
        [attr.aria-pressed]="selected()"
        [attr.aria-label]="ariaLabel()"
        (click)="onToggle($event)"
      >
        <svg lucideCheck [class]="checkClass()"></svg>
      </button>
    }
  `,
})
export class CardSelectionComponent {
  readonly selected = input(false, {transform: booleanAttribute});
  readonly selectionMode = input(false, {transform: booleanAttribute});
  readonly ariaLabel = input.required<string>();

  readonly selectionToggled = output<{shiftKey: boolean}>();

  protected readonly controlClass = computed(() =>
    cn(
      cardCheckboxBaseClass,
      this.selectionMode() ? '' : cardCheckboxInteractiveClass,
      this.selected() || this.selectionMode() ? 'opacity-100' : '',
      this.selected() && !this.selectionMode() ? 'pointer-events-auto' : '',
      this.selected() ? cardCheckboxSelectedClass : '',
    ),
  );
  protected readonly checkClass = computed(() =>
    cn(cardCheckIconClass, this.selected() ? 'opacity-100' : ''),
  );

  protected onToggle(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.selectionToggled.emit({shiftKey: event.shiftKey});
  }
}
