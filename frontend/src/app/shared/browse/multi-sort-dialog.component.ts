import {Component, inject, signal} from '@angular/core';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {TranslocoPipe} from '@jsverse/transloco';

import {type BrowseSortTerm} from '../../core/data/browse.models';
import {AppButtonComponent} from '../ui/button/app-button.component';
import {AppCheckboxComponent} from '../ui/checkbox/app-checkbox.component';
import {BrowseMultiSortEditorComponent} from './multi-sort-editor.component';
import {type BrowseSortField, type BrowseSortOption} from './sort';

export interface BrowseMultiSortDialogData<Key extends string = string> {
  readonly terms: readonly BrowseSortTerm<Key>[];
  readonly options: readonly BrowseSortOption<Key>[];
  readonly sortField: (key: Key) => BrowseSortField;
  readonly saveDefaultFor?: string;
}

export interface BrowseMultiSortDialogResult<Key extends string = string> {
  readonly terms: readonly BrowseSortTerm<Key>[];
  readonly saveAsDefault: boolean;
}

@Component({
  selector: 'app-browse-multi-sort-dialog',
  imports: [TranslocoPipe, AppButtonComponent, AppCheckboxComponent, BrowseMultiSortEditorComponent],
  template: `
    <div class="flex flex-col gap-4 rounded-[var(--p-dialog-border-radius)] border border-border bg-page p-5">
      <h2 class="text-lg font-semibold text-text">{{ 'browse.sort.multiSort' | transloco }}</h2>
      <app-browse-multi-sort-editor [(terms)]="draft" [options]="data.options" [sortField]="data.sortField" />
      @if (data.saveDefaultFor; as scope) {
        <label for="save-as-default" class="flex cursor-pointer select-none items-center gap-2 text-sm text-text">
          <app-checkbox inputId="save-as-default" [(checked)]="saveAsDefault" />
          {{ 'browse.sort.saveAsDefault' | transloco: {scope} }}
        </label>
      }
      <div class="flex justify-end gap-2">
        <app-button variant="ghost" [label]="'common.cancel' | transloco" (clicked)="cancel()" />
        <app-button variant="soft" tone="primary" [label]="'browse.sort.apply' | transloco" (clicked)="apply()" />
      </div>
    </div>
  `,
})
export class BrowseMultiSortDialogComponent {
  private readonly ref = inject(DynamicDialogRef);
  // SAFETY: the dialog is only opened by DialogLauncherService.openMultiSortDialog, which always passes data.
  protected readonly data = inject<DynamicDialogConfig<BrowseMultiSortDialogData>>(DynamicDialogConfig).data!;
  protected readonly draft = signal<readonly BrowseSortTerm[]>([...this.data.terms]);
  protected readonly saveAsDefault = signal(false);

  protected apply(): void {
    const result: BrowseMultiSortDialogResult = {
      terms: [...this.draft()],
      saveAsDefault: this.saveAsDefault(),
    };
    this.ref.close(result);
  }

  protected cancel(): void {
    this.ref.close();
  }
}
