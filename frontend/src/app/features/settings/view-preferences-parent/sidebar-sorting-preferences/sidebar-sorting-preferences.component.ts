import {Component, computed, inject} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
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
export class SidebarSortingPreferencesComponent {
  private readonly sortingOptionDefs = [
    {value: {field: 'name', order: 'asc'}, translationKey: 'nameAsc'},
    {value: {field: 'name', order: 'desc'}, translationKey: 'nameDesc'},
    {value: {field: 'id', order: 'asc'}, translationKey: 'creationAsc'},
    {value: {field: 'id', order: 'desc'}, translationKey: 'creationDesc'},
  ] satisfies {value: SortPref; translationKey: string}[];

  private readonly layoutService = inject(LayoutService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private readonly activeLang = toSignal(this.t.langChanges$, {initialValue: this.t.getActiveLang()});

  readonly selectedLibrarySorting = computed(() => this.layoutService.librarySort());
  readonly selectedShelfSorting = computed(() => this.layoutService.shelfSort());
  readonly selectedMagicShelfSorting = computed(() => this.layoutService.magicShelfSort());

  readonly sortingOptions = computed(() => {
    this.activeLang();
    return this.sortingOptionDefs.map(opt => ({
      ...opt,
      label: this.t.translate('settingsView.sidebarSort.' + opt.translationKey)
    }));
  });

  onLibrarySortingChange(value: SortPref) {
    this.layoutService.setLibrarySort(value);
    this.showSuccessToast();
  }

  onShelfSortingChange(value: SortPref) {
    this.layoutService.setShelfSort(value);
    this.showSuccessToast();
  }

  onMagicShelfSortingChange(value: SortPref) {
    this.layoutService.setMagicShelfSort(value);
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
