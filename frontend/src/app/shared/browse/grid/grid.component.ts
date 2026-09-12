import {NgTemplateOutlet} from '@angular/common';
import {
  Component,
  ElementRef,
  computed,
  contentChild,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {type VirtualItem} from '@tanstack/angular-virtual';

import {LayoutService} from '../../layout/layout.service';
import {AppButtonComponent} from '../../ui/button/app-button.component';
import {type GridDensityGrid} from '../../util/grid-density.util';
import {type VirtualGridScrollMode} from '../../util/virtual-grid.util';
import {type BrowseStatus} from '../results';
import {createBrowseSkeletonDelay} from '../skeleton-delay';
import {BrowseGridViewportComponent} from './grid-viewport.component';
import {BrowseEmptyDef, BrowseGridItemDef, BrowseGridSkeletonDef} from './grid.directives';

type BrowseGridViewState = 'idle' | 'skeleton' | 'empty' | 'initial-error' | 'grid';

@Component({
  selector: 'app-browse-grid',
  imports: [AppButtonComponent, NgTemplateOutlet, TranslocoPipe, BrowseGridViewportComponent],
  host: {
    class: 'block min-w-0',
    '[class.min-h-0]': "scrollMode() === 'element'",
    '[class.flex-1]': "scrollMode() === 'element'",
    '[class.overflow-y-auto]': "scrollMode() === 'element'",
    '[class.overscroll-y-contain]': "scrollMode() === 'element'",
  },
  template: `
    @if (viewState() === 'empty') {
      <ng-container [ngTemplateOutlet]="emptyDef().templateRef" />
    } @else if (viewState() === 'initial-error') {
      <div class="flex flex-col items-center gap-3 py-16 text-center">
        <p class="m-0 text-sm text-text-muted">{{ initialErrorMessage() }}</p>
        <app-button
          variant="soft"
          size="sm"
          [label]="'common.retry' | transloco"
          (clicked)="retryInitial.emit()" />
      </div>
    } @else if (viewState() === 'grid' || viewState() === 'skeleton') {
      @if (scrollMode() === 'element') {
        <ng-container [ngTemplateOutlet]="viewport" [ngTemplateOutletContext]="{scrollMode: 'element'}" />
      } @else {
        <ng-container [ngTemplateOutlet]="viewport" [ngTemplateOutletContext]="{scrollMode: 'window'}" />
      }
      <ng-template #viewport let-scrollMode="scrollMode">
        <app-browse-grid-viewport
          [scrollMode]="scrollMode"
          [scrollHost]="hostRef"
          [items]="items()"
          [itemKey]="itemKey()"
          [hasNextPage]="hasNextPage()"
          [fixedColumns]="fixedColumns()"
          [minItemWidth]="minItemWidth()"
          [gap]="gap()"
          [rowGap]="rowGap()"
          [estimateItemHeight]="estimateItemHeight()"
          [itemTemplate]="itemDef().templateRef"
          [skeletonTemplate]="skeletonDef().templateRef"
          [skeletonFill]="viewState() === 'skeleton'" />
      </ng-template>

      @if (viewState() === 'grid' && nextPageError()) {
        <div class="sticky bottom-4 z-10 mt-4 flex justify-center">
          <div class="flex items-center gap-3 rounded-lg border border-border bg-card px-4 py-2 shadow-pop">
            <span class="text-sm text-text-muted">{{ nextPageErrorMessage() }}</span>
            <app-button
              variant="ghost"
              size="sm"
              [label]="'common.retry' | transloco"
              (clicked)="retryNextPage.emit()" />
          </div>
        </div>
      }
    }
  `,
})
export class BrowseGridComponent<T> {
  private readonly layout = inject(LayoutService);
  protected readonly hostRef = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly items = input.required<readonly T[]>();
  readonly itemKey = input.required<(item: T) => VirtualItem['key']>();
  readonly hasNextPage = input.required<boolean>();
  readonly status = input.required<BrowseStatus>();
  readonly nextPageError = input.required<boolean>();
  readonly fixedColumns = input.required<number | undefined>();
  readonly minItemWidth = input.required<number>();
  readonly gap = input.required<number>();
  readonly rowGap = input.required<number | undefined>();
  readonly estimateItemHeight = input.required<(itemWidth: number) => number>();
  readonly initialErrorMessage = input.required<string>();
  readonly nextPageErrorMessage = input.required<string>();

  readonly renderedRange = computed(() => this.viewport()?.renderedRange() ?? null);
  readonly retryInitial = output<void>();
  readonly retryNextPage = output<void>();

  readonly itemDef = contentChild.required<BrowseGridItemDef<T>>(BrowseGridItemDef);
  readonly skeletonDef = contentChild.required(BrowseGridSkeletonDef);
  readonly emptyDef = contentChild.required(BrowseEmptyDef);

  private readonly viewport = viewChild(BrowseGridViewportComponent);

  protected readonly scrollMode = computed<VirtualGridScrollMode>(() => this.layout.isDesktop() ? 'element' : 'window');
  private readonly hasLoadedItems = computed(() => this.items().length > 0);
  private readonly skeletonVisible = createBrowseSkeletonDelay(this.status, this.hasLoadedItems);

  protected readonly viewState = computed<BrowseGridViewState>(() => {
    if (this.hasLoadedItems()) {
      return 'grid';
    }
    switch (this.status()) {
      case 'error':
        return 'initial-error';
      case 'success':
        return 'empty';
      default:
        return this.skeletonVisible() ? 'skeleton' : 'idle';
    }
  });

  densityGrid(): GridDensityGrid | null {
    return this.viewport()?.densityGrid() ?? null;
  }

  scrollToTop(): void {
    this.viewport()?.scrollToTop();
  }
}
