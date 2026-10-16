import {Component, computed, effect, inject} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {MessageService} from '@openng/optimus-ui/api';
import {Button} from '@openng/optimus-ui/button';
import {Checkbox} from '@openng/optimus-ui/checkbox';
import {Select} from '@openng/optimus-ui/select';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {ShelfDefinitionQueryService} from '../../../book/data/shelf-definition-query.service';
import {LibraryService} from '../../../book/service/library.service';
import {
  DashboardConfig,
  dashboardConfigOrDefault,
  DEFAULT_DASHBOARD_CONFIG,
  ScrollerConfig,
  ScrollerType,
} from '../../../dashboard/models/dashboard-config.model';
import {MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {UserService} from '../../user-management/user.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-dashboard-preferences',
  standalone: true,
  imports: [
    FormsModule,
    Button,
    Checkbox,
    Select,
    TranslocoDirective
  ],
  templateUrl: './dashboard-preferences.component.html',
  styleUrls: ['./dashboard-preferences.component.scss']
})
export class DashboardPreferencesComponent {
  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);
  private readonly magicShelfService = inject(MagicShelfService);
  private readonly libraryService = inject(LibraryService);
  private readonly shelfDefinitionQuery = inject(ShelfDefinitionQueryService);
  private readonly shelvesQuery = injectQuery(() => this.shelfDefinitionQuery.definitions());
  private readonly translocoService = inject(TranslocoService);
  private readonly activeLanguage = toSignal(this.translocoService.langChanges$, {
    initialValue: this.translocoService.getActiveLang()
  });

  config: DashboardConfig = this.savedConfig();

  readonly availableScrollerTypes = computed(() => {
    this.activeLanguage(); // Trigger on language change
    const t = (key: string) => this.translocoService.translate(`settingsView.dashboardScrollers.${key}`);
    return [
      {label: t('scrollerTypes.lastRead'), value: ScrollerType.LAST_READ},
      {label: t('scrollerTypes.lastListened'), value: ScrollerType.LAST_LISTENED},
      {label: t('scrollerTypes.latestAdded'), value: ScrollerType.LATEST_ADDED},
      {label: t('scrollerTypes.recentlyFinished'), value: ScrollerType.RECENTLY_FINISHED},
      {label: t('scrollerTypes.random'), value: ScrollerType.RANDOM},
      {label: t('scrollerTypes.library'), value: ScrollerType.LIBRARY},
      {label: t('scrollerTypes.shelf'), value: ScrollerType.SHELF},
      {label: t('scrollerTypes.magicShelf'), value: ScrollerType.MAGIC_SHELF}
    ];
  });

  readonly sortFieldOptions = computed(() => {
    this.activeLanguage();
    const t = (key: string) => this.translocoService.translate(`settingsView.dashboardScrollers.${key}`);
    return [
      {label: t('sortFields.title'), value: 'title'},
      {label: t('sortFields.addedOn'), value: 'addedOn'},
      {label: t('sortFields.author'), value: 'author'},
      {label: t('sortFields.authorSurnameVorname'), value: 'authorSurnameVorname'},
      {label: t('sortFields.seriesName'), value: 'seriesName'},
      {label: t('sortFields.seriesNumber'), value: 'seriesNumber'},
      {label: t('sortFields.personalRating'), value: 'personalRating'},
      {label: t('sortFields.publisher'), value: 'publisher'},
      {label: t('sortFields.publishedDate'), value: 'publishedDate'},
      {label: t('sortFields.lastReadTime'), value: 'lastReadTime'},
      {label: t('sortFields.readStatus'), value: 'readStatus'},
      {label: t('sortFields.dateFinished'), value: 'dateFinished'},
      {label: t('sortFields.readingProgress'), value: 'readingProgress'},
      {label: t('sortFields.pageCount'), value: 'pageCount'}
    ];
  });

  readonly sortDirectionOptions = computed(() => {
    this.activeLanguage();
    const t = (key: string) => this.translocoService.translate(`settingsView.dashboardScrollers.${key}`);
    return [
      {label: t('sortDirections.asc'), value: 'asc'},
      {label: t('sortDirections.desc'), value: 'desc'}
    ];
  });

  readonly magicShelves = computed(() =>
    this.magicShelfService.shelves().map(shelf => ({
      label: shelf.name,
      value: shelf.id!
    }))
  );

  readonly libraries = computed(() =>
    this.libraryService.libraries().flatMap(library =>
      library.id == null ? [] : [{label: library.name, value: library.id}]));

  readonly shelves = computed(() =>
    (this.shelvesQuery.data() ?? []).map(shelf => ({label: shelf.name, value: shelf.id})));

  readonly ScrollerType = ScrollerType;

  private readonly syncConfigEffect = effect(() => {
    this.config = this.savedConfig();
  });

  isScopeType(type: ScrollerType): boolean {
    return type === ScrollerType.LIBRARY || type === ScrollerType.SHELF || type === ScrollerType.MAGIC_SHELF;
  }

  addScroller(): void {
    const newId = (Math.max(...this.config.scrollers.map((s: ScrollerConfig) => parseInt(s.id)), 0) + 1).toString();
    this.config.scrollers.push({
      id: newId,
      type: ScrollerType.LATEST_ADDED,
      enabled: true,
      order: this.config.scrollers.length + 1
    });
  }

  removeScroller(index: number): void {
    if (this.config.scrollers.length <= 1) {
      return;
    }
    this.config.scrollers.splice(index, 1);
    this.updateOrder();
  }

  onScrollerTypeChange(scroller: ScrollerConfig): void {
    delete scroller.libraryId;
    delete scroller.shelfId;
    delete scroller.magicShelfId;
    if (!this.isScopeType(scroller.type)) {
      delete scroller.sortField;
      delete scroller.sortDirection;
    }
  }

  moveUp(index: number): void {
    if (index > 0) {
      [this.config.scrollers[index], this.config.scrollers[index - 1]] =
        [this.config.scrollers[index - 1], this.config.scrollers[index]];
      this.updateOrder();
    }
  }

  moveDown(index: number): void {
    if (index < this.config.scrollers.length - 1) {
      [this.config.scrollers[index], this.config.scrollers[index + 1]] =
        [this.config.scrollers[index + 1], this.config.scrollers[index]];
      this.updateOrder();
    }
  }

  private updateOrder(): void {
    this.config.scrollers.forEach((scroller, index) => {
      scroller.order = index + 1;
    });
  }

  save(): void {
    this.saveConfig(this.config);
    this.messageService.add({
      severity: 'success',
      summary: this.translocoService.translate('settingsView.dashboardScrollers.saveSuccess'),
      detail: this.translocoService.translate('settingsView.dashboardScrollers.saveSuccessDetail'),
      life: 1500,
    });
  }

  resetToDefault(): void {
    this.saveConfig(DEFAULT_DASHBOARD_CONFIG);
  }

  private savedConfig(): DashboardConfig {
    return structuredClone(dashboardConfigOrDefault(this.userService.currentUser()?.userSettings.dashboardConfig));
  }

  private saveConfig(config: DashboardConfig): void {
    const user = this.userService.currentUser();
    if (user) {
      this.userService.updateUserSetting(user.id, 'dashboardConfig', structuredClone(config));
    }
  }
}
