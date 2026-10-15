import {LiveAnnouncer} from '@angular/cdk/a11y';
import {CdkDrag, type CdkDragDrop, CdkDragHandle, CdkDropList, CdkDropListGroup} from '@angular/cdk/drag-drop';
import {
  afterNextRender,
  ApplicationRef,
  Component,
  computed,
  effect,
  ElementRef,
  forwardRef,
  inject,
  Injector,
  signal,
} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {
  LucideEllipsisVertical,
  type LucideIconData,
  LucideRotateCcw,
  LucideSquarePen,
} from '@lucide/angular';

import {AppPageHeaderComponent} from '../../../shared/layout/page-header/app.page-header.component';
import {type PageHeader} from '../../../shared/layout/page-header/page-header.service';
import {PageTitleService} from '../../../shared/service/page-title.service';
import {AppButtonComponent} from '../../../shared/ui/button/app-button.component';
import {AppMenuComponent} from '../../../shared/ui/menu/app-menu.component';
import {AppMenuContentDirective} from '../../../shared/ui/menu/app-menu-content.directive';
import {AppMenuItemComponent} from '../../../shared/ui/menu/app-menu-item.component';
import {AppMenuSeparatorComponent} from '../../../shared/ui/menu/app-menu-separator.component';
import {AppMenuTriggerDirective} from '../../../shared/ui/menu/app-menu-trigger.directive';
import {AppSelectComponent} from '../../../shared/ui/select/app-select.component';
import {type SelectOption} from '../../../shared/ui/select/app-select.options';
import {EMPTY_FACET_SELECTION} from '../../book/data/book-query-params';
import {LibraryService} from '../../book/service/library.service';
import {UserService} from '../../settings/user-management/user.service';
import {StatsCardComponent, type StatsCardEditing} from '../card/stats-card.component';
import {type StatsChartDefinition} from '../card/stats-chart-definition';
import {STATS_CONTEXT, STATS_PAGE, type StatsContext, type StatsPageId} from '../card/stats-context';
import {type StatsScopeParams} from '../data/stats-query-keys';
import {LIBRARY_STATS_CHARTS} from '../registry/library-stats.registry';
import {READING_STATS_CHARTS} from '../registry/reading-stats.registry';
import {createStatsChartGrid} from './stats-grid';
import {statsChartColumnClass} from './stats-layout';

interface StatsPageConfig {
  readonly titleKey: string;
  readonly documentTitleKey: string;
  readonly preferencesKey: string;
  readonly charts: readonly StatsChartDefinition[];
  readonly libraryPage: boolean;
}

const STATS_PAGES = {
  library: {
    titleKey: 'statsLibrary.main.title',
    documentTitleKey: 'statsLibrary.main.title',
    preferencesKey: 'libraryStatsPreferences',
    charts: LIBRARY_STATS_CHARTS,
    libraryPage: true,
  },
  reading: {
    titleKey: 'statsUser.main.title',
    documentTitleKey: 'statsUser.main.titleBarTitle',
    preferencesKey: 'readingStatsPreferences',
    charts: READING_STATS_CHARTS,
    libraryPage: false,
  },
} as const satisfies Record<StatsPageId, StatsPageConfig>;

type LibraryChoice = number | 'all';

@Component({
  selector: 'app-stats-page',
  imports: [
    AppButtonComponent,
    AppMenuComponent,
    AppMenuContentDirective,
    AppMenuItemComponent,
    AppMenuSeparatorComponent,
    AppMenuTriggerDirective,
    AppPageHeaderComponent,
    AppSelectComponent,
    CdkDrag,
    CdkDragHandle,
    CdkDropList,
    CdkDropListGroup,
    LucideEllipsisVertical,
    StatsCardComponent,
    TranslocoPipe,
  ],
  providers: [{provide: STATS_CONTEXT, useExisting: forwardRef(() => StatsPageComponent)}],
  templateUrl: './stats-page.component.html',
  host: {class: 'contents'},
})
export class StatsPageComponent implements StatsContext {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly applicationRef = inject(ApplicationRef);
  private readonly injector = inject(Injector);
  private readonly liveAnnouncer = inject(LiveAnnouncer);
  private readonly transloco = inject(TranslocoService);
  private readonly currentUser = inject(UserService).currentUser;
  private readonly libraries = inject(LibraryService).libraries;
  protected readonly config = STATS_PAGES[inject(STATS_PAGE)];
  private readonly activeLang = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  private readonly timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  protected readonly selectedLibrary = signal<LibraryChoice>('all');
  readonly scope = computed<StatsScopeParams>(() => {
    const library = this.selectedLibrary();
    return {
      facets: library === 'all' ? EMPTY_FACET_SELECTION : {library: [library.toString()]},
      facetLogic: 'and',
      tz: this.timeZone,
    };
  });

  protected readonly libraryOptions = computed<readonly SelectOption<LibraryChoice>[]>(() => {
    this.activeLang();
    return [
      {value: 'all', label: this.transloco.translate('statsLibrary.libraryFilter.allLibraries')},
      ...this.libraries().flatMap(({id, name}) => (id === undefined ? [] : [{value: id, label: name}])),
    ];
  });

  protected readonly grid = createStatsChartGrid({
    preferencesKey: this.config.preferencesKey,
    defaults: this.config.charts,
  });

  protected readonly isEditing = signal(false);
  protected readonly editingById = computed(() => {
    const charts = this.grid.enabledCharts();
    return new Map<string, StatsCardEditing>(charts.map(({id}, index) => [id, {
      canMoveEarlier: index > 0,
      canMoveLater: index < charts.length - 1,
    }]));
  });

  protected readonly pageHeader = computed<PageHeader>(() => {
    this.activeLang();
    const user = this.currentUser();
    return {title: this.transloco.translate(this.config.titleKey, {name: user ? user.name || user.username : ''})};
  });

  protected readonly iconEdit: LucideIconData = LucideSquarePen.icon;
  protected readonly iconReset: LucideIconData = LucideRotateCcw.icon;
  protected readonly chartDragStartDelay = {touch: 180, mouse: 0};
  protected readonly columnClass = statsChartColumnClass;

  constructor() {
    const pageTitle = inject(PageTitleService);
    effect(() => {
      this.activeLang();
      pageTitle.setPageTitle(this.transloco.translate(this.config.documentTitleKey));
    });
  }

  protected dropChart(event: CdkDragDrop<unknown, unknown, StatsChartDefinition>): void {
    this.grid.reorder(event.item.data, event.container.id, event.currentIndex);
    // Fixes CDK putting the cards in their old positions before the drag+drop new position is reported and causing flicker
    this.applicationRef.tick();
  }

  protected moveChart(chartId: string, delta: -1 | 1): void {
    this.grid.move(chartId, delta);
    const charts = this.grid.enabledCharts();
    const index = charts.findIndex(({id}) => id === chartId);
    void this.liveAnnouncer.announce(`${this.transloco.translate(charts[index].nameKey)}, ${index + 1} / ${charts.length}`);
    this.restoreChartFocus(chartId, delta);
  }

  protected removeChart(chartId: string): void {
    const chartIds = this.grid.enabledCharts().map(({id}) => id);
    const index = chartIds.indexOf(chartId);
    const nextChartId = chartIds[index + 1] ?? chartIds[index - 1];
    this.grid.remove(chartId);

    if (nextChartId) {
      this.restoreChartFocus(nextChartId);
    } else {
      this.restoreFocus('#stats-page-config-trigger');
    }
  }

  protected finishEditing(): void {
    this.isEditing.set(false);
    this.restoreFocus('#stats-page-config-trigger');
  }

  private restoreChartFocus(chartId: string, delta: -1 | 1 = -1): void {
    afterNextRender(() => {
      const directions = delta === -1 ? ['earlier', 'later'] : ['later', 'earlier'];
      for (const direction of directions) {
        const button = this.element.nativeElement.querySelector<HTMLButtonElement>(
          `[data-chart-move-${direction}="${chartId}"] button:not(:disabled)`,
        );
        if (button) {
          button.focus();
          break;
        }
      }
    }, {injector: this.injector});
  }

  private restoreFocus(selector: string): void {
    afterNextRender(
      () => this.element.nativeElement.querySelector<HTMLElement>(selector)?.focus(),
      {injector: this.injector},
    );
  }
}
