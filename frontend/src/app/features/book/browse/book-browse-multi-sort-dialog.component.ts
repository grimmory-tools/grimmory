import {Component, inject, signal} from '@angular/core';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {TranslocoPipe} from '@jsverse/transloco';

import {AppButtonComponent} from '../../../shared/ui/button/app-button.component';
import {AppCheckboxComponent} from '../../../shared/ui/checkbox/app-checkbox.component';
import {type BookSortTerm} from '../data/book-query-params';
import {BookBrowseMultiSortEditorComponent} from './book-browse-multi-sort-editor.component';
import {type BookSortOption} from './book-browse-sort';

export interface BookBrowseMultiSortDialogData {
  readonly terms: readonly BookSortTerm[];
  readonly options: readonly BookSortOption[];
  readonly saveDefaultFor?: string;
}

export interface BookBrowseMultiSortDialogResult {
  readonly terms: readonly BookSortTerm[];
  readonly saveAsDefault: boolean;
}

@Component({
  selector: 'app-book-browse-multi-sort-dialog',
  imports: [TranslocoPipe, AppButtonComponent, AppCheckboxComponent, BookBrowseMultiSortEditorComponent],
  template: `
    <div class="flex flex-col gap-4 rounded-[var(--p-dialog-border-radius)] border border-border bg-page p-5">
      <h2 class="text-lg font-semibold text-text">{{ 'browse.sort.multiSort' | transloco }}</h2>
      <app-book-browse-multi-sort-editor [(terms)]="draft" [options]="data.options" />
      @if (data.saveDefaultFor; as scope) {
        <label for="save-as-default" class="flex cursor-pointer select-none items-center gap-2 text-sm text-text">
          <app-checkbox inputId="save-as-default" [(checked)]="saveAsDefault" />
          {{ 'book.sorting.saveAsDefault' | transloco: {scope} }}
        </label>
      }
      <div class="flex justify-end gap-2">
        <app-button variant="ghost" [label]="'common.cancel' | transloco" (clicked)="cancel()" />
        <app-button variant="soft" tone="primary" [label]="'browse.sort.apply' | transloco" (clicked)="apply()" />
      </div>
    </div>
  `,
})
export class BookBrowseMultiSortDialogComponent {
  private readonly ref = inject(DynamicDialogRef);
  protected readonly data = inject<DynamicDialogConfig<BookBrowseMultiSortDialogData>>(DynamicDialogConfig).data!;
  protected readonly draft = signal<readonly BookSortTerm[]>([...this.data.terms]);
  protected readonly saveAsDefault = signal(false);

  protected apply(): void {
    const result: BookBrowseMultiSortDialogResult = {
      terms: [...this.draft()],
      saveAsDefault: this.saveAsDefault(),
    };
    this.ref.close(result);
  }

  protected cancel(): void {
    this.ref.close();
  }
}
