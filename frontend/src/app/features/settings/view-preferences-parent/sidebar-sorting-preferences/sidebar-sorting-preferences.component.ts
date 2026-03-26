import {Component, effect, inject, OnDestroy, OnInit} from '@angular/core';
import {Select} from 'primeng/select';
import {SidebarLibrarySorting, SidebarMagicShelfSorting, SidebarShelfSorting, User, UserService, UserSettings} from '../../user-management/user.service';
import {MessageService} from 'primeng/api';
import {Subject} from 'rxjs';
import {FormsModule} from '@angular/forms';
import {takeUntil} from 'rxjs/operators';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

type MutableSettingsBranch = Record<string, unknown>;

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
export class SidebarSortingPreferencesComponent implements OnInit, OnDestroy {

  private readonly sortingOptionDefs = [
    {value: {field: 'name', order: 'asc'}, translationKey: 'nameAsc'},
    {value: {field: 'name', order: 'desc'}, translationKey: 'nameDesc'},
    {value: {field: 'id', order: 'asc'}, translationKey: 'creationAsc'},
    {value: {field: 'id', order: 'desc'}, translationKey: 'creationDesc'},
  ];

  sortingOptions: {label: string; value: {field: string; order: string}; translationKey: string}[] = [];

  selectedLibrarySorting: SidebarLibrarySorting = {field: 'id', order: 'asc'};
  selectedShelfSorting: SidebarShelfSorting = {field: 'id', order: 'asc'};
  selectedMagicShelfSorting: SidebarMagicShelfSorting = {field: 'id', order: 'asc'};

  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  private currentUser: User | null = null;
  private hasInitialized = false;

  private readonly syncUserEffect = effect(() => {
    const user = this.userService.currentUser();
    if (!user) return;

    this.currentUser = user;
    if (!this.hasInitialized) {
      this.hasInitialized = true;
      this.loadPreferences(user.userSettings);
    }
  });

  ngOnInit(): void {
    this.buildSortingOptions();
    this.t.langChanges$.pipe(takeUntil(this.destroy$)).subscribe(() => this.buildSortingOptions());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private buildSortingOptions(): void {
    this.sortingOptions = this.sortingOptionDefs.map(opt => ({
      ...opt,
      label: this.t.translate('settingsView.sidebarSort.' + opt.translationKey)
    }));
  }

  private loadPreferences(settings: UserSettings): void {
    this.selectedLibrarySorting = settings.sidebarLibrarySorting;
    this.selectedShelfSorting = settings.sidebarShelfSorting;
    this.selectedMagicShelfSorting = settings.sidebarMagicShelfSorting;
  }

  private updatePreference(path: string[], value: unknown): void {
    if (!this.currentUser) return;
    let target = this.currentUser.userSettings as unknown as MutableSettingsBranch;
    for (let i = 0; i < path.length - 1; i++) {
      const next = target[path[i]];
      if (!next || typeof next !== 'object') {
        target[path[i]] = {};
      }
      target = target[path[i]] as MutableSettingsBranch;
    }
    target[path.at(-1)!] = value;

    const [rootKey] = path;
    const updatedValue = this.currentUser.userSettings[rootKey as keyof UserSettings];
    this.userService.updateUserSetting(this.currentUser.id, rootKey, updatedValue);
    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsView.sidebarSort.prefsUpdated'),
      detail: this.t.translate('settingsView.sidebarSort.prefsUpdatedDetail'),
      life: 1500
    });
  }

  onLibrarySortingChange() {
    this.updatePreference(['sidebarLibrarySorting'], this.selectedLibrarySorting);
  }

  onShelfSortingChange() {
    this.updatePreference(['sidebarShelfSorting'], this.selectedShelfSorting);
  }

  onMagicShelfSortingChange() {
    this.updatePreference(['sidebarMagicShelfSorting'], this.selectedMagicShelfSorting);
  }
}
