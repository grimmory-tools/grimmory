import {inject, Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';
import {MessageService} from 'primeng/api';
import {TableColumnPreference, UserService} from '../../../settings/user-management/user.service';

@Injectable({
  providedIn: 'root'
})
export class TableColumnPreferenceService {
  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);

  private readonly preferencesSubject = new BehaviorSubject<TableColumnPreference[]>([]);
  readonly preferences$ = this.preferencesSubject.asObservable();

  private readonly allAvailableColumns = [
    {field: 'readStatus', header: 'Read'},
    {field: 'title', header: 'Title'},
    {field: 'authors', header: 'Authors'},
    {field: 'publisher', header: 'Publisher'},
    {field: 'seriesName', header: 'Series'},
    {field: 'seriesNumber', header: 'Series #'},
    {field: 'categories', header: 'Genres'},
    {field: 'publishedDate', header: 'Published'},
    {field: 'lastReadTime', header: 'Last Read'},
    {field: 'addedOn', header: 'Added'},
    {field: 'fileSizeKb', header: 'File Size'},
    {field: 'language', header: 'Language'},
    {field: 'isbn', header: 'ISBN'},
    {field: 'pageCount', header: 'Pages'},
    {field: 'amazonRating', header: 'Amazon'},
    {field: 'amazonReviewCount', header: 'AZ #'},
    {field: 'goodreadsRating', header: 'Goodreads'},
    {field: 'goodreadsReviewCount', header: 'GR #'},
    {field: 'hardcoverRating', header: 'Hardcover'},
    {field: 'hardcoverReviewCount', header: 'HC #'},
  ];

  private readonly fallbackPreferences: TableColumnPreference[] = this.allAvailableColumns.map((col, index) => ({
    field: col.field,
    visible: true,
    order: index
  }));

  initPreferences(savedPrefs: TableColumnPreference[] | undefined): void {
    const effectivePrefs = savedPrefs?.length ? savedPrefs : this.fallbackPreferences;
    this.preferencesSubject.next(this.mergeWithAllColumns(effectivePrefs));
  }

  get allColumns(): { field: string; header: string }[] {
    return this.allAvailableColumns;
  }

  get visibleColumns(): { field: string; header: string }[] {
    return this.preferencesSubject.value
      .filter(pref => pref.visible)
      .sort((a, b) => a.order - b.order)
      .map(pref => ({
        field: pref.field,
        header: this.getColumnHeader(pref.field)
      }));
  }

  get preferences(): TableColumnPreference[] {
    return this.preferencesSubject.value;
  }

  saveVisibleColumns(selectedColumns: { field: string }[]): void {
    const selectedFieldSet = new Set(selectedColumns.map(c => c.field));

    const updatedPreferences: TableColumnPreference[] = this.allAvailableColumns.map((col, index) => {
      const selectionIndex = selectedColumns.findIndex(c => c.field === col.field);
      return {
        field: col.field,
        visible: selectedFieldSet.has(col.field),
        order: selectionIndex >= 0 ? selectionIndex : index
      };
    });

    this.preferencesSubject.next(updatedPreferences);

    const currentUser = this.userService.getCurrentUser();
    if (!currentUser) return;

    this.userService.updateUserSetting(currentUser.id, 'tableColumnPreference', updatedPreferences);

    this.messageService.add({
      severity: 'success',
      summary: 'Preferences Saved',
      detail: 'Your column layout has been saved.',
      life: 1500
    });
  }

  private getColumnHeader(field: string): string {
    return this.allAvailableColumns.find(col => col.field === field)?.header ?? field;
  }

  private mergeWithAllColumns(savedPrefs: TableColumnPreference[]): TableColumnPreference[] {
    const savedPrefMap = new Map(savedPrefs.map(p => [p.field, p]));

    return this.allAvailableColumns.map((col, index) => {
      const saved = savedPrefMap.get(col.field);
      return {
        field: col.field,
        visible: saved?.visible ?? true,
        order: saved?.order ?? index
      };
    });
  }
}
