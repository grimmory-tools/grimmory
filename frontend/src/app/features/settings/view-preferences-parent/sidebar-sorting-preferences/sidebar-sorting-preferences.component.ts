import {Component, DestroyRef, effect, inject, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Select} from 'primeng/select';
import {MessageService} from 'primeng/api';
import {FormsModule} from '@angular/forms';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {SortPref} from '../../../../shared/layout/sidebar-sort-preferences';

@Component({
  selector: 'app-sidebar-sorting-preferences',
  imports: [
    Select,
    FormsModule,
    TranslocoDirective
  ],
  templateUrl: './sidebar-sorting-preferences.component.html',
  styleUrl: './sidebar-sorting-preferences.component.scss'
})
export class SidebarSortingPreferencesComponent implements OnInit {
  private readonly sortingOptionDefs = [
    {value: {field: 'name', order: 'asc'}, translationKey: 'nameAsc'},
    {value: {field: 'name', order: 'desc'}, translationKey: 'nameDesc'},
    {value: {field: 'id', order: 'asc'}, translationKey: 'creationAsc'},
    {value: {field: 'id', order: 'desc'}, translationKey: 'creationDesc'},
  ] satisfies {value: SortPref; translationKey: string}[];

  sortingOptions: {label: string; value: SortPref; translationKey: string}[] = [];

  selectedLibrarySorting: SortPref = {field: 'id', order: 'asc'};
  selectedShelfSorting: SortPref = {field: 'id', order: 'asc'};
  selectedMagicShelfSorting: SortPref = {field: 'id', order: 'asc'};

  private readonly layoutService = inject(LayoutService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    effect(() => {
      this.selectedLibrarySorting = this.layoutService.librarySort();
      this.selectedShelfSorting = this.layoutService.shelfSort();
      this.selectedMagicShelfSorting = this.layoutService.magicShelfSort();
    });
  }

  ngOnInit(): void {
    this.buildSortingOptions();
    this.t.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.buildSortingOptions());
  }

  private buildSortingOptions(): void {
    this.sortingOptions = this.sortingOptionDefs.map(opt => ({
      ...opt,
      label: this.t.translate('settingsView.sidebarSort.' + opt.translationKey)
    }));
  }

  onLibrarySortingChange() {
    this.layoutService.setLibrarySort(this.selectedLibrarySorting);
    this.showSuccessToast();
  }

  onShelfSortingChange() {
    this.layoutService.setShelfSort(this.selectedShelfSorting);
    this.showSuccessToast();
  }

  onMagicShelfSortingChange() {
    this.layoutService.setMagicShelfSort(this.selectedMagicShelfSorting);
    this.showSuccessToast();
  }

  private showSuccessToast(): void {
    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsView.sidebarSort.prefsUpdated'),
      detail: this.t.translate('settingsView.sidebarSort.prefsUpdatedDetail'),
      life: 1500
    });
  }
}
