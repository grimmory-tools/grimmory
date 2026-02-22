import {Component, DestroyRef, inject, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {ButtonModule} from 'primeng/button';
import {CheckboxModule} from 'primeng/checkbox';
import {InputTextModule} from 'primeng/inputtext';
import {SelectModule} from 'primeng/select';
import {InputNumberModule} from 'primeng/inputnumber';
import {DashboardConfig, ScrollerConfig, ScrollerType} from '../../models/dashboard-config.model';
import {DashboardConfigService} from '../../services/dashboard-config.service';
import {MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {map} from 'rxjs/operators';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

export const MAX_SCROLLERS = 10;
export const DEFAULT_MAX_ITEMS = 20;
export const MIN_ITEMS = 10;
export const MAX_ITEMS = 20;

@Component({
  selector: 'app-dashboard-settings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    CheckboxModule,
    InputTextModule,
    SelectModule,
    InputNumberModule,
    TranslocoDirective
  ],
  templateUrl: './dashboard-settings.component.html',
  styleUrls: ['./dashboard-settings.component.scss']
})
export class DashboardSettingsComponent implements OnInit {
  private configService = inject(DashboardConfigService);
  private dialogRef = inject(DynamicDialogRef);
  private magicShelfService = inject(MagicShelfService);
  private translocoService = inject(TranslocoService);
  private destroyRef = inject(DestroyRef);

  config!: DashboardConfig;

  availableScrollerTypes: {label: string; value: ScrollerType}[] = [];
  sortFieldOptions: {label: string; value: string}[] = [];
  sortDirectionOptions: {label: string; value: string}[] = [];
  upNextModeOptions: {label: string; value: boolean}[] = [];
  readAgainSortOptions: {label: string; value: boolean}[] = [];

  magicShelves$ = this.magicShelfService.shelvesState$.pipe(
    map(state => (state.shelves || []).map(shelf => ({
      label: shelf.name,
      value: shelf.id!
    })))
  );

  private magicShelvesMap = new Map<number, string>();

  readonly MIN_ITEMS = MIN_ITEMS;
  readonly MAX_ITEMS = MAX_ITEMS;
  readonly MAX_SCROLLERS = MAX_SCROLLERS;

  ngOnInit(): void {
    this.translocoService.langChanges$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.buildTranslatedOptions());

    this.configService.config$.subscribe(config => {
      this.config = JSON.parse(JSON.stringify(config));
    });

    this.magicShelfService.shelvesState$.subscribe(state => {
      this.magicShelvesMap.clear();
      (state.shelves || []).forEach(shelf => {
        if (shelf.id) {
          this.magicShelvesMap.set(shelf.id, shelf.name);
        }
      });
    });
  }

  private buildTranslatedOptions(): void {
    const t = (key: string) => this.translocoService.translate(`dashboard.settings.${key}`);

    this.availableScrollerTypes = [
      {label: t('scrollerTypes.lastRead'), value: ScrollerType.LAST_READ},
      {label: t('scrollerTypes.lastListened'), value: ScrollerType.LAST_LISTENED},
      {label: t('scrollerTypes.latestAdded'), value: ScrollerType.LATEST_ADDED},
      {label: t('scrollerTypes.random'), value: ScrollerType.RANDOM},
      {label: t('scrollerTypes.magicShelf'), value: ScrollerType.MAGIC_SHELF},
      {label: t('scrollerTypes.upNext'), value: ScrollerType.UP_NEXT},
      {label: t('scrollerTypes.readAgain'), value: ScrollerType.READ_AGAIN},
      {label: t('scrollerTypes.recommendations'), value: ScrollerType.RECOMMENDATIONS}
    ];

    this.sortFieldOptions = [
      {label: t('sortFields.title'), value: 'title'},
      {label: t('sortFields.fileName'), value: 'fileName'},
      {label: t('sortFields.filePath'), value: 'filePath'},
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
      {label: t('sortFields.bookType'), value: 'bookType'},
      {label: t('sortFields.pageCount'), value: 'pageCount'}
    ];

    this.sortDirectionOptions = [
      {label: t('sortDirections.asc'), value: 'asc'},
      {label: t('sortDirections.desc'), value: 'desc'}
    ];

    this.upNextModeOptions = [
      {label: t('upNextModes.nextInSeries'), value: false},
      {label: t('upNextModes.firstUnread'), value: true}
    ];

    this.readAgainSortOptions = [
      {label: t('readAgainSorts.random'), value: false},
      {label: t('readAgainSorts.recentlyFinished'), value: true}
    ];
  }

  getScrollerTitle(scroller: ScrollerConfig): string {
    if (scroller.type === ScrollerType.MAGIC_SHELF && scroller.magicShelfId) {
      return this.magicShelvesMap.get(scroller.magicShelfId) || 'dashboard.scroller.magicShelf';
    }

    switch (scroller.type) {
      case ScrollerType.LAST_READ:
        return 'dashboard.scroller.continueReading';
      case ScrollerType.LAST_LISTENED:
        return 'dashboard.scroller.continueListening';
      case ScrollerType.LATEST_ADDED:
        return 'dashboard.scroller.recentlyAdded';
      case ScrollerType.RANDOM:
        return 'dashboard.scroller.discoverNew';
      case ScrollerType.UP_NEXT:
        return 'dashboard.scroller.upNext';
      case ScrollerType.READ_AGAIN:
        return 'dashboard.scroller.readAgain';
      case ScrollerType.RECOMMENDATIONS:
        return 'dashboard.scroller.recommendations';
      default:
        return 'dashboard.scroller.default';
    }
  }

  addScroller(): void {
    if (this.config.scrollers.length >= MAX_SCROLLERS) {
      return;
    }
    const newId = (Math.max(...this.config.scrollers.map((s: ScrollerConfig) => parseInt(s.id)), 0) + 1).toString();
    this.config.scrollers.push({
      id: newId,
      type: ScrollerType.LATEST_ADDED,
      title: '',
      enabled: true,
      order: this.config.scrollers.length + 1,
      maxItems: DEFAULT_MAX_ITEMS
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
    // Clean up type-specific fields when switching away from that type
    if (scroller.type !== ScrollerType.MAGIC_SHELF) {
      delete scroller.magicShelfId;
      delete scroller.sortField;
      delete scroller.sortDirection;
    }

    if (scroller.type === ScrollerType.UP_NEXT) {
      // Initialize to default if not already set
      if (scroller.upNextShowFirstUnread === undefined) {
        scroller.upNextShowFirstUnread = false;
      }
    } else {
      delete scroller.upNextShowFirstUnread;
    }

    if (scroller.type === ScrollerType.READ_AGAIN) {
      // Initialize to default if not already set
      if (scroller.readAgainSortByFinished === undefined) {
        scroller.readAgainSortByFinished = false;
      }
    } else {
      delete scroller.readAgainSortByFinished;
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
    this.config.scrollers.forEach(scroller => {
      scroller.title = this.getScrollerTitle(scroller);
    });
    this.configService.saveConfig(this.config);
    this.dialogRef.close();
  }

  cancel(): void {
    this.dialogRef.close();
  }

  resetToDefault(): void {
    this.configService.resetToDefault();
    this.dialogRef.close();
  }
}
