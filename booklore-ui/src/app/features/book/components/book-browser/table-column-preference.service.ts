import {inject, Injectable, signal} from '@angular/core';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {TableColumnPreference, UserService} from '../../../settings/user-management/user.service';

@Injectable({
  providedIn: 'root'
})
export class TableColumnPreferenceService {
  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  private readonly _preferences = signal<TableColumnPreference[]>([]);
  readonly preferences = this._preferences.asReadonly();

  private readonly allAvailableFields = [
    'readStatus', 'title', 'authors', 'publisher', 'seriesName', 'seriesNumber',
    'categories', 'publishedDate', 'lastReadTime', 'addedOn', 'fileName', 'fileSizeKb',
    'language', 'isbn', 'pageCount', 'amazonRating', 'amazonReviewCount',
    'goodreadsRating', 'goodreadsReviewCount', 'hardcoverRating', 'hardcoverReviewCount',
    'ranobedbRating',
  ];

  private readonly fallbackPreferences: TableColumnPreference[] = this.allAvailableFields.map((field, index) => ({
    field,
    visible: true,
    order: index
  }));

  initPreferences(savedPrefs: TableColumnPreference[] | undefined): void {
    const effectivePrefs = savedPrefs?.length ? savedPrefs : this.fallbackPreferences;
    this._preferences.set(this.mergeWithAllColumns(effectivePrefs));
  }

  get allColumns(): { field: string; header: string }[] {
    return this.allAvailableFields.map(field => ({
      field,
      header: this.t.translate(`book.columnPref.columns.${field}`)
    }));
  }

  get visibleColumns(): { field: string; header: string }[] {
    return this.preferences()
      .filter(pref => pref.visible)
      .sort((a, b) => a.order - b.order)
      .map(pref => ({
        field: pref.field,
        header: this.t.translate(`book.columnPref.columns.${pref.field}`)
      }));
  }

  saveVisibleColumns(selectedColumns: { field: string }[]): void {
    const selectedFieldSet = new Set(selectedColumns.map(c => c.field));

    const updatedPreferences: TableColumnPreference[] = this.allAvailableFields.map((field, index) => {
      const selectionIndex = selectedColumns.findIndex(c => c.field === field);
      return {
        field,
        visible: selectedFieldSet.has(field),
        order: selectionIndex >= 0 ? selectionIndex : index
      };
    });

    this._preferences.set(updatedPreferences);

    const currentUser = this.userService.getCurrentUser();
    if (!currentUser) return;

    this.userService.updateUserSetting(currentUser.id, 'tableColumnPreference', updatedPreferences);

    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('book.columnPref.toast.savedSummary'),
      detail: this.t.translate('book.columnPref.toast.savedDetail'),
      life: 1500
    });
  }

  private mergeWithAllColumns(savedPrefs: TableColumnPreference[]): TableColumnPreference[] {
    const savedPrefMap = new Map(savedPrefs.map(p => [p.field, p]));

    return this.allAvailableFields.map((field, index) => {
      const saved = savedPrefMap.get(field);
      return {
        field,
        visible: saved?.visible ?? true,
        order: saved?.order ?? index
      };
    });
  }
}
