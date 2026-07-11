import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  linkedSignal,
  output,
  viewChild,
  viewChildren,
  type ElementRef,
  type TemplateRef,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ComboboxPopupContainer } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import {
  CdkConnectedOverlay,
  type ConnectedPosition,
  Overlay,
  OverlayModule,
  type ScrollStrategy,
} from '@angular/cdk/overlay';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideLoaderCircle } from '@lucide/angular';

import { cn } from '../cn';
import {
  connectedOverlayPanelClass,
  connectedOverlayPositions,
  connectedOverlayScrollStrategy,
  refreshConnectedOverlayPosition,
} from '../connected-overlay';
import {
  overlayListEmptyItemClass,
  overlayListOptionClass,
  overlayListRootClass,
  overlayListSurfaceClass,
} from '../overlay-list.styles';
import { type AppAutocompleteOption } from './app-autocomplete-option';
import { type AppAutocompleteOptionContext } from './app-autocomplete-option-template.directive';

const LOAD_MORE_PREFETCH_PX = 240;

@Component({
  selector: 'app-autocomplete-popup',
  standalone: true,
  imports: [NgTemplateOutlet, OverlayModule, ComboboxPopupContainer, Listbox, Option, TranslocoPipe, LucideLoaderCircle],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-template ngComboboxPopupContainer>
      <ng-template
        [cdkConnectedOverlay]="{
          origin: origin(),
          usePopover: 'inline',
          matchWidth: true,
          positions: overlayPositions,
          viewportMargin: 8,
          push: true
        }"
        [cdkConnectedOverlayOpen]="open()"
        [cdkConnectedOverlayScrollStrategy]="overlayScrollStrategy"
        (attach)="onOverlayAttach()">
        <div [class]="surfaceClass">
          <ul
            ngListbox
            #list
            tabindex="-1"
            focusMode="activedescendant"
            selectionMode="explicit"
            [readonly]="readonly()"
            [disabled]="disabled()"
            [attr.aria-busy]="isBusy() ? 'true' : null"
            (valuesChange)="onValuesChange($event)"
            [class]="listClass">
            @for (option of options(); track option.value) {
              <li
                ngOption
                tabindex="-1"
                [value]="option.value"
                [label]="option.label"
                [class]="optionClass"
                (pointerdown)="onOptionPointerdown($event, option)">
                @if (optionTemplate(); as template) {
                  <ng-container *ngTemplateOutlet="template; context: { $implicit: option }" />
                } @else {
                  <span class="truncate leading-5">{{ option.label }}</span>
                }
              </li>
            } @empty {
              @if (pending()) {
                <li [class]="loadingRowClass" role="presentation">
                  <svg lucideLoaderCircle class="size-4 animate-spin text-text-muted motion-reduce:animate-none" aria-hidden="true"></svg>
                  <span class="sr-only">{{ 'shared.ui.spinner.loading' | transloco }}</span>
                </li>
              } @else if (errored()) {
                <li [class]="emptyClass" role="presentation">{{ 'shared.ui.autocomplete.loadFailed' | transloco }}</li>
              } @else {
                <li [class]="emptyClass" role="presentation">{{ emptyMessage() || ('shared.ui.autocomplete.noResults' | transloco) }}</li>
              }
            }
            @if (showLoadError()) {
              <li [class]="emptyClass" role="presentation">{{ 'shared.ui.autocomplete.loadFailed' | transloco }}</li>
            } @else if (showSentinel()) {
              <li #sentinel [class]="loadingRowClass" role="presentation" aria-hidden="true">
                <svg lucideLoaderCircle class="size-4 animate-spin text-text-muted motion-reduce:animate-none" aria-hidden="true"></svg>
              </li>
            }
          </ul>
        </div>
      </ng-template>
    </ng-template>
  `,
})
export class AppAutocompletePopupComponent {
  readonly origin = input.required<ElementRef<HTMLElement> | HTMLElement>();
  readonly open = input(false);
  readonly disabled = input(false);
  readonly readonly = input(false);
  readonly pending = input(false);
  readonly loadingMore = input(false);
  readonly hasMore = input(false);
  readonly errored = input(false);
  readonly suggestions = input<readonly AppAutocompleteOption[]>([]);
  readonly emptyMessage = input('');
  readonly optionTemplate = input<TemplateRef<AppAutocompleteOptionContext> | null>(null);

  readonly optionSelected = output<AppAutocompleteOption>();
  readonly loadMore = output<void>();

  private readonly overlay = viewChild(CdkConnectedOverlay);
  private readonly listbox = viewChild<Listbox<string>>(Listbox);
  private readonly optionRefs = viewChildren<Option<string>>(Option);
  private readonly listRef = viewChild<ElementRef<HTMLElement>>('list');
  private readonly sentinelRef = viewChild<ElementRef<HTMLElement>>('sentinel');
  private readonly overlayService = inject(Overlay);

  protected readonly overlayPositions: ConnectedPosition[] = connectedOverlayPositions;
  protected readonly overlayScrollStrategy: ScrollStrategy = connectedOverlayScrollStrategy(this.overlayService);
  protected readonly surfaceClass = cn(overlayListSurfaceClass, connectedOverlayPanelClass, 'box-border w-full p-0');
  protected readonly optionClass = cn(overlayListOptionClass, 'shrink-0');
  protected readonly emptyClass = overlayListEmptyItemClass;
  protected readonly loadingRowClass = cn(overlayListEmptyItemClass, 'justify-center');
  protected readonly listClass = cn(
    overlayListRootClass,
    'max-h-60 overflow-y-auto overscroll-contain p-1 [scrollbar-gutter:stable]',
  );

  protected readonly options = computed(() => {
    const seen = new Set<string>();
    return this.suggestions().filter((option) => {
      if (seen.has(option.value)) return false;
      seen.add(option.value);
      return true;
    });
  });
  protected readonly showSentinel = computed(() => this.hasMore() && this.options().length > 0);
  protected readonly showLoadError = computed(() => this.errored() && this.options().length > 0 && !this.loadingMore());
  protected readonly isBusy = computed(() => this.pending() || this.loadingMore());
  private readonly loadMoreRequested = linkedSignal({
    source: () => ({ options: this.options(), loadingMore: this.loadingMore() }),
    computation: () => false,
  });
  private leadingOptionValue: string | null = null;

  constructor() {
    effect((onCleanup) => {
      this.options();
      const list = this.listRef()?.nativeElement;
      const sentinel = this.sentinelRef()?.nativeElement;
      if (!list || !sentinel) return;

      const observer = new IntersectionObserver(
        (entries) => {
          if (entries.some((entry) => entry.isIntersecting)) this.requestLoadMore();
        },
        { root: list, rootMargin: `0px 0px ${LOAD_MORE_PREFETCH_PX}px 0px` },
      );
      observer.observe(sentinel);
      onCleanup(() => observer.disconnect());
    });

    afterRenderEffect(() => {
      const leading = this.options()[0]?.value ?? null;
      this.pending();
      this.errored();
      this.showSentinel();

      const list = this.listRef()?.nativeElement;
      if (list && leading !== this.leadingOptionValue) list.scrollTop = 0;
      this.leadingOptionValue = leading;
      refreshConnectedOverlayPosition(this.overlay());
    });
  }

  activeOption(): AppAutocompleteOption | null {
    const active = this.optionRefs().find((option) => option.active());
    if (!active) return null;
    const value = active.value();
    return this.options().find((option) => option.value === value) ?? null;
  }

  clearSelection(): void {
    this.listbox()?.values.set([]);
  }

  protected onOverlayAttach(): void {
    refreshConnectedOverlayPosition(this.overlay());
  }

  protected onValuesChange(values: readonly string[] | Set<string>): void {
    const picked = this.lastSelectedValue(values);
    if (!picked) return;

    this.clearSelection();
    const option = this.options().find((candidate) => candidate.value === picked);
    if (option) this.optionSelected.emit(option);
  }

  protected onOptionPointerdown(event: PointerEvent, option: AppAutocompleteOption): void {
    event.preventDefault();
    event.stopPropagation();
    this.clearSelection();
    this.optionSelected.emit(option);
  }

  private requestLoadMore(): void {
    if (
      this.disabled() ||
      this.readonly() ||
      this.pending() ||
      this.loadingMore() ||
      this.errored() ||
      !this.hasMore() ||
      this.loadMoreRequested()
    ) return;

    this.loadMoreRequested.set(true);
    this.loadMore.emit();
  }

  private lastSelectedValue(values: readonly string[] | Set<string>): string | null {
    const selected = Array.isArray(values) ? values : [...values];
    return selected[selected.length - 1] ?? null;
  }
}
