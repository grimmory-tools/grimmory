import {Component, ElementRef, inject, OnDestroy, OnInit, viewChild} from '@angular/core';
import {Select} from 'primeng/select';
import {ALL_FILTER_OPTION_VALUES, ALL_FILTER_OPTIONS, BookFilterMode, DEFAULT_VISIBLE_FILTERS, User, UserService, UserSettings, UserState, VisibleFilterType} from '../../user-management/user.service';
import {FILTER_LABEL_KEYS} from '../../../book/components/book-browser/book-filter/book-filter.config';
import {MessageService} from 'primeng/api';
import {Observable, Subject} from 'rxjs';
import {FormsModule} from '@angular/forms';
import {filter, takeUntil} from 'rxjs/operators';
import {CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray} from '@angular/cdk/drag-drop';
import {Tooltip} from 'primeng/tooltip';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

const MIN_VISIBLE_FILTERS = 5;
const MAX_VISIBLE_FILTERS = 20;

@Component({
  selector: 'app-filter-preferences',
  imports: [
    Select,
    FormsModule,
    CdkDropList,
    CdkDrag,
    CdkDragHandle,
    Tooltip,
    TranslocoDirective,
    TranslocoPipe
  ],
  templateUrl: './filter-preferences.component.html',
  styleUrl: './filter-preferences.component.scss'
})
export class FilterPreferencesComponent implements OnInit, OnDestroy {

  readonly filterModes = [
    {label: 'And', value: 'and'},
    {label: 'Or', value: 'or'},
    {label: 'Single', value: 'single'},
  ];

  readonly allFilterOptions = ALL_FILTER_OPTIONS;
  readonly minFilters = MIN_VISIBLE_FILTERS;
  readonly maxFilters = MAX_VISIBLE_FILTERS;

  selectedFilterMode: BookFilterMode = 'and';
  selectedVisibleFilters: VisibleFilterType[] = [...DEFAULT_VISIBLE_FILTERS];

  private readonly filterList = viewChild<ElementRef<HTMLElement>>('filterList');

  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  userData$: Observable<UserState> = this.userService.userState$;
  private currentUser: User | null = null;

  ngOnInit(): void {
    this.userData$.pipe(
      filter(userState => !!userState?.user && userState.loaded),
      takeUntil(this.destroy$)
    ).subscribe(userState => {
      this.currentUser = userState.user;
      this.loadPreferences(userState.user!.userSettings);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadPreferences(settings: UserSettings): void {
    this.selectedFilterMode = settings.filterMode ?? 'and';
    this.selectedVisibleFilters = settings.visibleFilters ?? [...DEFAULT_VISIBLE_FILTERS];
  }

  private updatePreference(path: string[], value: unknown): void {
    if (!this.currentUser) return;

    let target: any = this.currentUser.userSettings;
    for (let i = 0; i < path.length - 1; i++) {
      target = target[path[i]] ||= {};
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

  onFilterModeChange(): void {
    this.updatePreference(['filterMode'], this.selectedFilterMode);
  }

  selectedAddFilter: string | null = null;

  get availableFilters(): {label: string; value: string}[] {
    const used = new Set(this.selectedVisibleFilters);
    return ALL_FILTER_OPTION_VALUES
      .filter(v => !used.has(v))
      .map(v => ({label: this.getFilterLabel(v), value: v}));
  }

  getFilterLabel(value: string): string {
    const key = FILTER_LABEL_KEYS[value as keyof typeof FILTER_LABEL_KEYS];
    return key ? this.t.translate(key) : value;
  }

  onDrop(event: CdkDragDrop<VisibleFilterType[]>): void {
    moveItemInArray(this.selectedVisibleFilters, event.previousIndex, event.currentIndex);
    this.updatePreference(['visibleFilters'], this.selectedVisibleFilters);
  }

  addFilter(): void {
    if (this.selectedAddFilter) {
      this.selectedVisibleFilters.push(this.selectedAddFilter as VisibleFilterType);
      this.selectedAddFilter = null;
      this.updatePreference(['visibleFilters'], this.selectedVisibleFilters);
      requestAnimationFrame(() => {
        const el = this.filterList()?.nativeElement;
        if (el) el.scrollTop = el.scrollHeight;
      });
    }
  }

  removeFilter(index: number): void {
    if (this.selectedVisibleFilters.length > MIN_VISIBLE_FILTERS) {
      this.selectedVisibleFilters.splice(index, 1);
      this.updatePreference(['visibleFilters'], this.selectedVisibleFilters);
    }
  }

  resetToDefaults(): void {
    this.selectedVisibleFilters = [...DEFAULT_VISIBLE_FILTERS];
    this.updatePreference(['visibleFilters'], this.selectedVisibleFilters);
  }

  get selectionCountText(): string {
    return this.t.translate('settingsView.filter.selectionCount', {count: this.selectedVisibleFilters.length, total: this.allFilterOptions.length});
  }
}
