import {ChangeDetectionStrategy, Component, computed, ElementRef, input, linkedSignal, output, viewChild} from '@angular/core';
import {Menu} from '@angular/aria/menu';
import {TranslocoPipe} from '@jsverse/transloco';

import {LucideSearch} from '@lucide/angular';

import {AppMenuComponent} from '../../ui/menu/app-menu.component';
import {AppMenuCheckboxComponent} from '../../ui/menu/app-menu-checkbox.component';
import {AppMenuContentDirective} from '../../ui/menu/app-menu-content.directive';
import {AppMenuItemComponent} from '../../ui/menu/app-menu-item.component';
import {AppMenuSeparatorComponent} from '../../ui/menu/app-menu-separator.component';

export interface ShelfMembershipItem {
  id: number;
  name: string;
  checked: boolean;
  mixed?: boolean;
}

const FILTER_THRESHOLD = 8;

@Component({
  selector: 'app-shelf-membership-menu',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {class: 'contents'},
  imports: [
    TranslocoPipe,
    LucideSearch,
    AppMenuComponent,
    AppMenuCheckboxComponent,
    AppMenuContentDirective,
    AppMenuItemComponent,
    AppMenuSeparatorComponent,
  ],
  template: `
    <app-menu
      [ariaLabel]="'shared.ui.shelfMenu.addToShelf' | transloco"
      (opened)="onOpened()"
      (closed)="onClosed()"
    >
      <ng-template appMenuContent>
        @if (filterVisible()) {
          <div class="flex items-center gap-2 rounded-sm px-2 pb-2 pt-1.5 focus-within:bg-surface-hover focus-within:text-text-strong">
            <svg lucideSearch class="size-3.5 shrink-0 text-text-muted" aria-hidden="true"></svg>
            <input
              #filterInput
              type="text"
              autocomplete="off"
              class="w-full min-w-0 bg-transparent text-sm text-text outline-none placeholder:text-text-muted"
              [value]="query()"
              [placeholder]="'shared.ui.select.search' | transloco"
              [attr.aria-label]="'shared.ui.select.search' | transloco"
              (input)="query.set(filterInput.value)"
              (keydown)="onFilterKeydown($event)"
            />
          </div>
          <app-menu-separator />
        }
        <div class="max-h-80 overflow-y-auto overscroll-contain">
          @for (shelf of filteredShelves(); track shelf.id) {
            <app-menu-checkbox
              [value]="shelf.id"
              [checked]="shelf.checked"
              [mixed]="shelf.mixed ?? false"
              (selected)="toggleShelf.emit({shelfId: shelf.id, checked: $event})">{{ shelf.name }}</app-menu-checkbox>
          } @empty {
            @if (shelves().length > 0) {
              <p role="status" class="m-0 flex min-h-7 items-center px-2 py-1 text-sm leading-5 text-text-muted pointer-coarse:min-h-11 pointer-coarse:px-3">{{ 'shared.ui.select.noResults' | transloco }}</p>
            }
          }
        </div>
        @if (shelves().length > 0) {
          <app-menu-separator />
        }
        <app-menu-item value="create-shelf" (selected)="createShelf.emit()">{{ 'shared.ui.shelfMenu.newShelf' | transloco }}</app-menu-item>
      </ng-template>
    </app-menu>
  `,
})
export class ShelfMembershipMenuComponent {
  readonly shelves = input.required<ShelfMembershipItem[]>();

  readonly toggleShelf = output<{shelfId: number; checked: boolean}>();
  readonly createShelf = output<void>();

  readonly menu = viewChild.required(AppMenuComponent);
  readonly ariaMenu = viewChild.required(AppMenuComponent, {read: Menu});

  private readonly filterInput = viewChild<ElementRef<HTMLInputElement>>('filterInput');

  private readonly filterRequired = computed(() => this.shelves().length > FILTER_THRESHOLD);
  protected readonly filterVisible = linkedSignal<boolean, boolean>({
    source: this.filterRequired,
    computation: (required, previous) => required || (previous?.value ?? false),
  });
  protected readonly query = linkedSignal<boolean, string>({
    source: this.filterRequired,
    computation: (required, previous) => required ? (previous?.value ?? '') : '',
  });
  protected readonly filteredShelves = computed(() => {
    if (!this.filterVisible()) {
      return this.shelves();
    }
    const needle = this.query().trim().toLowerCase();
    if (!needle) {
      return this.shelves();
    }
    return this.shelves().filter(shelf => shelf.name.toLowerCase().includes(needle));
  });

  protected onOpened(): void {
    this.filterVisible.set(this.filterRequired());
    queueMicrotask(() => this.filterInput()?.nativeElement.focus({preventScroll: true}));
  }

  protected onClosed(): void {
    this.query.set('');
    this.filterVisible.set(false);
  }

  protected onFilterKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      event.stopPropagation();
      const key = event.key === 'ArrowDown' ? 'Home' : 'End';
      this.ariaMenu().element.dispatchEvent(new KeyboardEvent('keydown', {key, bubbles: true, cancelable: true}));
      return;
    }
    if (event.key === 'Escape' || event.key === 'Tab') {
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    event.stopPropagation();
  }
}
