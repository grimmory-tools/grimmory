import {Component, computed, effect, inject} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {Button} from 'primeng/button';
import {Checkbox} from 'primeng/checkbox';
import {Select} from 'primeng/select';
import {InputNumber} from 'primeng/inputnumber';
import {DashboardConfig, ScrollerConfig, ScrollerType} from '../../models/dashboard-config.model';
import {DashboardConfigService} from '../../services/dashboard-config.service';
import {MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

export const MAX_SCROLLERS = 5;
export const DEFAULT_MAX_ITEMS = 20;
export const MIN_ITEMS = 10;
export const MAX_ITEMS = 20;

@Component({
  selector: 'app-dashboard-settings',
  standalone: true,
  imports: [
    FormsModule,
    Button,
    Checkbox,
    Select,
    InputNumber,
    TranslocoDirective,
    TranslocoPipe
  ],
  templateUrl: './dashboard-settings.component.html',
  styleUrls: ['./dashboard-settings.component.scss']
})
export class DashboardSettingsComponent {
  private readonly configService = inject(DashboardConfigService);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly magicShelfService = inject(MagicShelfService);
  private readonly translocoService = inject(TranslocoService);
  private readonly activeLanguage = toSignal(this.translocoService.langChanges$, {
    initialValue: this.translocoService.getActiveLang()
  });

  config: DashboardConfig = structuredClone(this.configService.config());

  readonly availableScrollerTypes = computed(() => {
    this.activeLanguage(); // Trigger on language change
    const t = (key: string) => this.translocoService.translate(`dashboard.settings.${key}`);
    return [
      {label: t('scrollerTypes.lastRead'), value: ScrollerType.LAST_READ},
      {label: t('scrollerTypes.lastListened'), value: ScrollerType.LAST_LISTENED},
      {label: t('scrollerTypes.latestAdded'), value: ScrollerType.LATEST_ADDED},
      {label: t('scrollerTypes.random'), value: ScrollerType.RANDOM},
      {label: t('scrollerTypes.magicShelf'), value: ScrollerType.MAGIC_SHELF}
    ];
  });

  readonly sortFieldOptions = computed(() => {
    this.activeLanguage();
    const t = (key: string) => this.translocoService.translate(`dashboard.settings.${key}`);
    return [
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
  });

  readonly sortDirectionOptions = computed(() => {
    this.activeLanguage();
    const t = (key: string) => this.translocoService.translate(`dashboard.settings.${key}`);
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

  private readonly magicShelvesMap = computed(() => {
    const shelfMap = new Map<number, string>();
    this.magicShelfService.shelves().forEach(shelf => {
      if (shelf.id) {
        shelfMap.set(shelf.id, shelf.name);
      }
    });
    return shelfMap;
  });

  readonly MIN_ITEMS = MIN_ITEMS;
  readonly MAX_ITEMS = MAX_ITEMS;

  private readonly syncConfigEffect = effect(() => {
    this.config = structuredClone(this.configService.config());
  });

  getScrollerTitle(scroller: ScrollerConfig): string {
    if (scroller.type === ScrollerType.MAGIC_SHELF && scroller.magicShelfId) {
      return this.magicShelvesMap().get(scroller.magicShelfId) || 'dashboard.scroller.magicShelf';
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
    if (scroller.type === ScrollerType.MAGIC_SHELF) {
      scroller.magicShelfId = undefined;
    } else {
      delete scroller.magicShelfId;
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
