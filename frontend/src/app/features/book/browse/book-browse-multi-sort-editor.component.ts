import {Component, computed, inject, input, model, signal} from '@angular/core';
import {CdkDrag, CdkDragHandle, CdkDropList, moveItemInArray, type CdkDragDrop} from '@angular/cdk/drag-drop';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {LucideChevronDown, LucideChevronUp, LucideDynamicIcon, LucideGripVertical, LucideX} from '@lucide/angular';

import {AppButtonComponent} from '../../../shared/ui/button/app-button.component';
import {AppSelectComponent} from '../../../shared/ui/select/app-select.component';
import {type SelectOption} from '../../../shared/ui/select/app-select.options';
import {type BookSortTerm} from '../data/book-query-params';
import {bookSortDirectionIcon, bookSortField, type BookSortOption} from './book-browse-sort';

@Component({
  selector: 'app-book-browse-multi-sort-editor',
  imports: [
    TranslocoPipe,
    AppButtonComponent,
    AppSelectComponent,
    CdkDrag,
    CdkDragHandle,
    CdkDropList,
    LucideChevronDown,
    LucideChevronUp,
    LucideDynamicIcon,
    LucideGripVertical,
    LucideX,
  ],
  template: `
    <div cdkDropList class="flex flex-col gap-1.5" (cdkDropListDropped)="drop($event)">
    @for (row of rows(); track row.key) {
      <div
        cdkDrag
        class="flex min-h-10 items-center gap-2 rounded-md border border-border bg-card py-1 pl-1 pr-2
          [.cdk-drop-list-dragging_&]:transition-transform [.cdk-drop-list-dragging_&]:duration-[250ms] [.cdk-drop-list-dragging_&]:ease-out
          [&.cdk-drag-animating]:transition-transform [&.cdk-drag-animating]:duration-[250ms] [&.cdk-drag-animating]:ease-out
          [&.cdk-drag-placeholder]:opacity-0
          [&.cdk-drag-preview]:border-primary [&.cdk-drag-preview]:shadow-pop">
        <span cdkDragHandle class="flex h-8 w-6 shrink-0 cursor-grab items-center justify-center text-text-muted">
          <svg lucideGripVertical class="size-4" aria-hidden="true"></svg>
        </span>
        <span class="flex size-5 shrink-0 items-center justify-center rounded-sm bg-page text-xs font-medium tabular-nums text-text-muted">{{ $index + 1 }}</span>
        <span class="min-w-0 flex-1 truncate text-sm text-text">{{ row.label }}</span>
        <app-button
          class="pointer-coarse:hidden"
          variant="ghost"
          iconOnly
          [disabled]="$index === 0"
          [ariaLabel]="'browse.sort.moveUp' | transloco"
          (clicked)="move($index, -1)">
          <svg lucideChevronUp aria-hidden="true"></svg>
        </app-button>
        <app-button
          class="pointer-coarse:hidden"
          variant="ghost"
          iconOnly
          [disabled]="$index === rows().length - 1"
          [ariaLabel]="'browse.sort.moveDown' | transloco"
          (clicked)="move($index, 1)">
          <svg lucideChevronDown aria-hidden="true"></svg>
        </app-button>
        <app-button
          variant="ghost"
          iconOnly
          [disabled]="!row.canFlip"
          [ariaLabel]="'browse.sort.flipDirection' | transloco"
          (clicked)="flip($index)">
          <svg [lucideIcon]="row.icon" aria-hidden="true"></svg>
        </app-button>
        <app-button
          variant="ghost"
          iconOnly
          [disabled]="rows().length === 1"
          [ariaLabel]="'common.remove' | transloco"
          (clicked)="removeAt($index)">
          <svg lucideX aria-hidden="true"></svg>
        </app-button>
      </div>
    }
    </div>
    <app-select
      class="mt-3 block"
      [options]="addOptions()"
      [(value)]="addSelection"
      [placeholder]="'browse.sort.addField' | transloco"
      [ariaLabel]="'browse.sort.addField' | transloco"
      (valueChange)="add($event)" />
  `,
})
export class BookBrowseMultiSortEditorComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly activeLang = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  readonly terms = model.required<readonly BookSortTerm[]>();
  readonly options = input.required<readonly BookSortOption[]>();

  protected readonly addSelection = signal<string | null>(null);

  protected readonly rows = computed(() => {
    this.activeLang();
    const optionsById = new Map(this.options().map(option => [option.id, option]));
    return this.terms().map(term => {
      const option = optionsById.get(term.key);
      return {
        key: term.key,
        label: this.transloco.translate((option ?? bookSortField(term.key)).labelKey),
        icon: bookSortDirectionIcon(term.key, term.direction),
        canFlip: option?.directions.length === 2,
      };
    });
  });

  protected readonly addOptions = computed<SelectOption<string>[]>(() => {
    this.activeLang();
    const used = new Set(this.terms().map(term => term.key));
    return this.options()
      .filter(option => !used.has(option.id))
      .map(option => ({value: option.id, label: this.transloco.translate(option.labelKey)}));
  });

  protected drop(event: CdkDragDrop<unknown>): void {
    if (event.previousIndex === event.currentIndex) {
      return;
    }
    const next = [...this.terms()];
    moveItemInArray(next, event.previousIndex, event.currentIndex);
    this.terms.set(next);
  }

  protected move(index: number, delta: -1 | 1): void {
    const next = [...this.terms()];
    const target = index + delta;
    [next[index], next[target]] = [next[target], next[index]];
    this.terms.set(next);
  }

  protected flip(index: number): void {
    const next = this.terms().map((term, i): BookSortTerm => {
      if (i !== index) {
        return term;
      }
      return {key: term.key, direction: term.direction === 'asc' ? 'desc' : 'asc'};
    });
    this.terms.set(next);
  }

  protected removeAt(index: number): void {
    const terms = this.terms();
    this.terms.set(terms.filter((_, i) => i !== index));
  }

  protected add(key: string | null): void {
    if (!key) {
      return;
    }
    const option = this.options().find(candidate => candidate.id === key);
    if (!option) {
      return;
    }
    this.terms.set([...this.terms(), {key: option.id, direction: option.defaultDirection}]);
    this.addSelection.set(null);
  }
}
