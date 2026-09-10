import {Component, computed, DestroyRef, effect, inject, Injector, OnInit} from '@angular/core';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Button} from '@openng/optimus-ui/button';

import {MessageService} from '@openng/optimus-ui/api';
import {Select} from '@openng/optimus-ui/select';
import {TableModule} from '@openng/optimus-ui/table';
import {User, UserService} from '../../user-management/user.service';
import {LibraryService} from '../../../book/service/library.service';
import {ShelfService} from '../../../book/service/shelf.service';
import {MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {FormsModule} from '@angular/forms';

import {Tooltip} from '@openng/optimus-ui/tooltip';
import {ToggleSwitch} from '@openng/optimus-ui/toggleswitch';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {take} from 'rxjs/operators';

import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {browseSortCriteria} from '../../../../shared/browse/sort';
import {
  DEFAULT_BOOK_SORT_TERMS,
  EMPTY_FACET_SELECTION,
  type BookSortTerm,
} from '../../../book/data/book-query-params';
import {BookQueryService} from '../../../book/data/book-query.service';
import {bookSortOptions, bookSortTermsFromCriteria} from '../../../book/browse/book-browse-sort';
import {entityViewSortCriteria, entityViewSortPatch} from '../../user-management/entity-view-preferences';
import {type BookBrowseMultiSortDialogResult} from '../../../book/browse/book-browse-multi-sort-dialog.component';
import {BookBrowseMultiSortEditorComponent} from '../../../book/browse/book-browse-multi-sort-editor.component';

@Component({
  selector: 'app-view-preferences',
  standalone: true,
  imports: [
    Select,
    FormsModule,
    Button,
    TableModule,
    Tooltip,
    ToggleSwitch,
    TranslocoDirective,
    BookBrowseMultiSortEditorComponent
  ],
  templateUrl: './view-preferences.component.html',
  styleUrl: './view-preferences.component.scss'
})
export class ViewPreferencesComponent implements OnInit {
  private t = inject(TranslocoService);

  entityTypeOptions: {label: string; value: string; translationKey: string}[] = [];

  viewModeOptions: {label: string; value: string; translationKey: string}[] = [];

  get libraryOptions(): { label: string; value: number }[] {
    return this.libraryService.libraries()
      .filter(library => library.id !== undefined)
      .map(library => ({label: library.name, value: library.id!}));
  }
  get shelfOptions(): { label: string; value: number }[] {
    return this.shelfService.shelves()
      .filter(shelf => shelf.id !== undefined)
      .map(shelf => ({label: shelf.name, value: shelf.id!}));
  }
  get magicShelfOptions(): { label: string; value: number }[] {
    return this.magicShelfService.shelves()
      .filter(shelf => shelf.id !== undefined)
      .map(shelf => ({label: shelf.name, value: shelf.id!}));
  }

  selectedView: 'GRID' | 'TABLE' = 'GRID';
  overlayBookType: boolean = true;
  autoSaveMetadata: boolean = false;
  globalSortTerms: readonly BookSortTerm[] = [];

  private readonly bookQuery = inject(BookQueryService);
  private readonly sortTokensQuery = injectQuery(() =>
    this.bookQuery.facets({facets: EMPTY_FACET_SELECTION, facetLogic: 'or'}));
  readonly editorSortOptions = computed(() =>
    bookSortOptions(this.sortTokensQuery.data()?.sortTokens ?? []));

  overrides: {
    entityType: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF';
    library: number;
    sortTerms: readonly BookSortTerm[];
    view: 'GRID' | 'TABLE';
  }[] = [];

  private user: User | null = null;
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  private libraryService = inject(LibraryService);
  private shelfService = inject(ShelfService);
  private magicShelfService = inject(MagicShelfService);
  private userService = inject(UserService);
  private messageService = inject(MessageService);
  private readonly dialogLauncher = inject(DialogLauncherService);
  private readonly currentUser = this.userService.currentUser;
  private hasInitializedPreferences = false;

  ngOnInit(): void {
    this.rebuildTranslatedLabels();
    this.t.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.rebuildTranslatedLabels();
    });

    effect(() => {
      const user = this.currentUser();
      if (this.hasInitializedPreferences || !user) {
        return;
      }

      this.hasInitializedPreferences = true;
      this.user = user;
      const prefs = user.userSettings?.entityViewPreferences;
      const global = prefs?.global;
      this.selectedView = global?.view ?? 'GRID';
      this.overlayBookType = global.overlayBookType ?? true;
      this.autoSaveMetadata = user.userSettings?.autoSaveMetadata ?? false;

      const storedTerms = bookSortTermsFromCriteria(entityViewSortCriteria(global));
      this.globalSortTerms = storedTerms.length > 0 ? storedTerms : DEFAULT_BOOK_SORT_TERMS;

      this.overrides = (prefs.overrides ?? []).map(override => ({
        entityType: override.entityType,
        library: override.entityId,
        sortTerms: bookSortTermsFromCriteria(entityViewSortCriteria(override.preferences)),
        view: override.preferences.view
      }));
    }, {injector: this.injector});
  }

  private rebuildTranslatedLabels(): void {
    this.entityTypeOptions = [
      {label: this.t.translate('settingsView.librarySort.entityLibrary'), value: 'LIBRARY', translationKey: 'entityLibrary'},
      {label: this.t.translate('settingsView.librarySort.entityShelf'), value: 'SHELF', translationKey: 'entityShelf'},
      {label: this.t.translate('settingsView.librarySort.entityMagicShelf'), value: 'MAGIC_SHELF', translationKey: 'entityMagicShelf'}
    ];
    this.viewModeOptions = [
      {label: this.t.translate('settingsView.librarySort.viewGrid'), value: 'GRID', translationKey: 'viewGrid'},
      {label: this.t.translate('settingsView.librarySort.viewTable'), value: 'TABLE', translationKey: 'viewTable'}
    ];
  }

  getAvailableEntities(index: number, type: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF') {
    const selected = this.overrides.map((o, i) => i !== index ? o.library : null);
    let source: { label: string; value: number }[];
    switch (type) {
      case 'LIBRARY':
        source = this.libraryOptions;
        break;
      case 'SHELF':
        source = this.shelfOptions;
        break;
      case 'MAGIC_SHELF':
        source = this.magicShelfOptions;
        break;
      default:
        source = [];
    }
    return source.filter(opt => !selected.includes(opt.value) || this.overrides[index]?.library === opt.value);
  }

  get availableLibraries() {
    const used = new Set(this.overrides.map(o => `${o.entityType}_${o.library}`));

    const withEntityType = (options: { label: string; value: number }[], entityType: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF') =>
      options.map(opt => ({...opt, entityType}));

    return [...withEntityType(this.libraryOptions, 'LIBRARY'),
      ...withEntityType(this.shelfOptions, 'SHELF'),
      ...withEntityType(this.magicShelfOptions, 'MAGIC_SHELF')]
      .filter(opt => !used.has(`${opt.entityType}_${opt.value}`));
  }

  addOverride(): void {
    const next = this.availableLibraries[0];
    if (next) {
      this.overrides.push({
        entityType: next.entityType,
        library: next.value,
        sortTerms: DEFAULT_BOOK_SORT_TERMS,
        view: 'GRID'
      });
    }
  }

  removeOverride(index: number): void {
    this.overrides.splice(index, 1);
  }

  onGlobalSortTermsChange(terms: readonly BookSortTerm[]): void {
    this.globalSortTerms = terms;
  }

  async editOverrideSort(index: number): Promise<void> {
    const ref = await this.dialogLauncher.openMultiSortDialog({
      terms: this.overrides[index].sortTerms,
      options: this.editorSortOptions(),
    });
    ref?.onClose.pipe(take(1)).subscribe((result?: BookBrowseMultiSortDialogResult) => {
      if (!result) {
        return;
      }
      this.overrides = this.overrides.map((override, i) =>
        i === index ? {...override, sortTerms: result.terms} : override);
    });
  }

  saveSettings(): void {
    if (!this.user) return;

    const prefs = structuredClone(this.user.userSettings.entityViewPreferences ?? {});

    const sortPatch = (terms: readonly BookSortTerm[]) =>
      entityViewSortPatch(browseSortCriteria(terms.length > 0 ? terms : DEFAULT_BOOK_SORT_TERMS));
    prefs.global = {
      ...prefs.global,
      ...sortPatch(this.globalSortTerms),
      view: this.selectedView,
      overlayBookType: this.overlayBookType
    };

    prefs.overrides = this.overrides.map(o => {
      const existing = prefs.overrides?.find(p =>
        p.entityId === o.library && p.entityType === o.entityType
      )?.preferences;

      return {
        entityType: o.entityType,
        entityId: o.library,
        preferences: {
          ...(existing ?? prefs.global),
          ...sortPatch(o.sortTerms),
          view: o.view
        }
      };
    });

    this.userService.updateUserSetting(this.user.id, 'entityViewPreferences', prefs);
    this.userService.updateUserSetting(this.user.id, 'autoSaveMetadata', this.autoSaveMetadata);

    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsView.librarySort.saveSuccess'),
      detail: this.t.translate('settingsView.librarySort.saveSuccessDetail')
    });
  }
}
