import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Button} from 'primeng/button';

import {MessageService} from 'primeng/api';
import {Select} from 'primeng/select';
import {TableModule} from 'primeng/table';
import {User, UserService} from '../../user-management/user.service';
import {LibraryService} from '../../../book/service/library.service';
import {ShelfService} from '../../../book/service/shelf.service';
import {combineLatest, Subject} from 'rxjs';
import {FormsModule} from '@angular/forms';
import {ToastModule} from 'primeng/toast';
import {filter, take, takeUntil} from 'rxjs/operators';

@Component({
  selector: 'app-view-preferences',
  standalone: true,
  imports: [
    Select,
    FormsModule,
    Button,
    TableModule,
    ToastModule
  ],
  templateUrl: './view-preferences.component.html',
  styleUrl: './view-preferences.component.scss'
})
export class ViewPreferencesComponent implements OnInit, OnDestroy {
  sortOptions = [
    {label: 'Title', field: 'title'},
    {label: 'Title + Series', field: 'titleSeries'},
    {label: 'Author', field: 'author'},
    {label: 'Last Read', field: 'lastReadTime'},
    {label: 'Added On', field: 'addedOn'},
    {label: 'File Size', field: 'fileSizeKb'},
    {label: 'Locked', field: 'locked'},
    {label: 'Publisher', field: 'publisher'},
    {label: 'Published Date', field: 'publishedDate'},
    {label: 'Amazon Rating', field: 'amazonRating'},
    {label: 'Amazon #', field: 'amazonReviewCount'},
    {label: 'Goodreads Rating', field: 'goodreadsRating'},
    {label: 'Goodreads #', field: 'goodreadsReviewCount'},
    {label: 'Hardcover Rating', field: 'hardcoverRating'},
    {label: 'Hardcover #', field: 'hardcoverReviewCount'},
    {label: 'Pages', field: 'pageCount'}
  ];

  entityTypeOptions = [
    {label: 'Library', value: 'LIBRARY'},
    {label: 'Shelf', value: 'SHELF'}
  ];

  sortDirectionOptions = [
    {label: 'Ascending', value: 'ASC'},
    {label: 'Descending', value: 'DESC'}
  ];

  viewModeOptions = [
    {label: 'Grid', value: 'GRID'},
    {label: 'Table', value: 'TABLE'}
  ];

  libraryOptions: { label: string; value: number }[] = [];
  shelfOptions: { label: string; value: number }[] = [];

  selectedSort: string = 'title';
  selectedSortDir: 'ASC' | 'DESC' = 'ASC';
  selectedView: 'GRID' | 'TABLE' = 'GRID';

  overrides: {
    entityType: 'LIBRARY' | 'SHELF';
    library: number;
    sort: string;
    sortDir: 'ASC' | 'DESC';
    view: 'GRID' | 'TABLE';
  }[] = [];

  private user: User | null = null;
  private readonly destroy$ = new Subject<void>();

  private libraryService = inject(LibraryService);
  private shelfService = inject(ShelfService);
  private userService = inject(UserService);
  private messageService = inject(MessageService);

  ngOnInit(): void {
    combineLatest([
      this.userService.userState$.pipe(filter(userState => !!userState?.user && userState.loaded), take(1)),
      this.libraryService.libraryState$.pipe(filter(libraryState => !!libraryState?.libraries && libraryState.loaded), take(1)),
      this.shelfService.shelfState$.pipe(filter(shelfState => !!shelfState?.shelves && shelfState.loaded), take(1)),
    ]).pipe(
      takeUntil(this.destroy$)
    ).subscribe(([userState, librariesState, shelfState]) => {

      this.user = userState.user;
      const prefs = userState.user?.userSettings?.entityViewPreferences;
      const global = prefs?.global;
      this.selectedSort = global?.sortKey ?? 'title';
      this.selectedSortDir = global?.sortDir ?? 'ASC';
      this.selectedView = global?.view ?? 'GRID';

      this.overrides = (prefs?.overrides ?? []).map(o => ({
        entityType: o.entityType,
        library: o.entityId,
        sort: o.preferences.sortKey,
        sortDir: o.preferences.sortDir ?? 'ASC',
        view: o.preferences.view ?? 'GRID'
      }));

      this.libraryOptions = (librariesState.libraries ?? []).filter(lib => lib.id !== undefined).map(lib => ({
        label: lib.name,
        value: lib.id!
      }));

      this.shelfOptions = (shelfState?.shelves ?? []).filter(s => s.id !== undefined).map(s => ({
        label: s.name,
        value: s.id!
      }));
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  getAvailableEntities(index: number, type: 'LIBRARY' | 'SHELF') {
    const selected = this.overrides.map((o, i) => i !== index ? o.library : null);
    const source = type === 'LIBRARY' ? this.libraryOptions : this.shelfOptions;
    return source.filter(opt => !selected.includes(opt.value) || this.overrides[index]?.library === opt.value);
  }

  get availableLibraries() {
    const used = new Set(this.overrides.map(o => `${o.entityType}_${o.library}`));

    const withEntityType = (options: { label: string; value: number }[], entityType: 'LIBRARY' | 'SHELF') =>
      options.map(opt => ({...opt, entityType}));

    return [...withEntityType(this.libraryOptions, 'LIBRARY'), ...withEntityType(this.shelfOptions, 'SHELF')]
      .filter(opt => !used.has(`${opt.entityType}_${opt.value}`));
  }

  addOverride(): void {
    const next = this.availableLibraries[0];
    if (next) {
      this.overrides.push({
        entityType: next.entityType,
        library: next.value,
        sort: 'title',
        sortDir: 'ASC',
        view: 'GRID'
      });
    }
  }

  removeOverride(index: number): void {
    this.overrides.splice(index, 1);
  }

  saveSettings(): void {
    if (!this.user) return;

    const prefs = structuredClone(this.user.userSettings.entityViewPreferences ?? {});

    prefs.global = {
      ...prefs.global,
      sortKey: this.selectedSort,
      sortDir: this.selectedSortDir,
      view: this.selectedView
    };

    prefs.overrides = this.overrides.map(o => {
      const existing = prefs.overrides?.find(p =>
        p.entityId === o.library && p.entityType === o.entityType
      )?.preferences;

      return {
        entityType: o.entityType,
        entityId: o.library,
        preferences: {
          sortKey: o.sort,
          sortDir: o.sortDir,
          view: o.view,
          coverSize: existing?.coverSize ?? 1.0,
          seriesCollapsed: existing?.seriesCollapsed ?? false
        }
      };
    });

    this.userService.updateUserSetting(this.user.id, 'entityViewPreferences', prefs);

    this.messageService.add({
      severity: 'success',
      summary: 'Preferences Saved',
      detail: 'Your sorting and view preferences were saved successfully.'
    });
  }
}
