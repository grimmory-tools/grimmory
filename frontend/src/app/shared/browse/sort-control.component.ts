import {Component, computed, inject, input, output, signal} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {
  LucideChevronDown,
  LucideChevronUp,
  LucideDynamicIcon,
  LucideListOrdered,
  type LucideIconData,
} from '@lucide/angular';
import {take} from 'rxjs/operators';

import {type BrowseSortDirection, type BrowseSortTerm} from '../../core/data/browse.models';
import {DialogLauncherService} from '../services/dialog-launcher.service';
import {AppButtonComponent} from '../ui/button/app-button.component';
import {connectedGroupClass, connectedItemClass} from '../ui/connected-group';
import {AppMenuComponent} from '../ui/menu/app-menu.component';
import {AppMenuItemComponent} from '../ui/menu/app-menu-item.component';
import {AppMenuSeparatorComponent} from '../ui/menu/app-menu-separator.component';
import {AppMenuTriggerDirective} from '../ui/menu/app-menu-trigger.directive';
import {type BrowseMultiSortDialogResult} from './multi-sort-dialog.component';
import {browseSortDirectionIcon, type BrowseSortField, type BrowseSortOption} from './sort';

@Component({
  selector: 'app-browse-sort-control',
  imports: [
    TranslocoPipe,
    AppButtonComponent,
    AppMenuComponent,
    AppMenuItemComponent,
    AppMenuSeparatorComponent,
    AppMenuTriggerDirective,
    LucideDynamicIcon,
  ],
  host: {class: 'contents'},
  templateUrl: './sort-control.component.html',
})
export class BrowseSortControlComponent<Key extends string> {
  private readonly dialogLauncher = inject(DialogLauncherService);

  readonly sortOptions = input.required<readonly BrowseSortOption<Key>[]>();
  readonly sortTerms = input.required<readonly BrowseSortTerm<Key>[]>();
  readonly sortField = input.required<(key: Key) => BrowseSortField>();
  readonly saveDefaultFor = input<string>();

  readonly sortTermsChange = output<readonly BrowseSortTerm<Key>[]>();
  readonly shuffleRequested = output();
  readonly sortDefaultSaved = output<readonly BrowseSortTerm<Key>[]>();

  protected readonly sortGroupClass = connectedGroupClass;
  protected readonly sortFieldButtonClass = computed(() => connectedItemClass({
    first: true,
    last: !this.activeSortHasSecondaryAction(),
  }));
  protected readonly sortDirectionButtonClass = connectedItemClass({first: false, last: true});

  protected readonly commonOptions = computed(() =>
    this.sortOptions().filter(option => option.group === 'common'));
  protected readonly moreOptions = computed(() =>
    this.sortOptions().filter(option => option.group === 'more'));
  protected readonly multiSortActive = computed(() => this.sortTerms().length > 1);
  protected readonly multiSortShortcut = computed(() =>
    this.multiSortActive() ? `+${this.sortTerms().length - 1}` : '');
  protected readonly moreSortsExpanded = signal(false);
  protected readonly moreSortsToggleIcon = computed<LucideIconData>(() =>
    this.moreSortsExpanded() ? LucideChevronUp.icon : LucideChevronDown.icon);
  protected readonly multiSortIcon: LucideIconData = LucideListOrdered.icon;

  private readonly primaryTerm = computed<BrowseSortTerm<Key>>(() => this.sortTerms()[0]);
  protected readonly activeId = computed(() => this.primaryTerm().key);
  private readonly activeDirection = computed(() => this.primaryTerm().direction);
  private readonly activeOption = computed(() =>
    this.sortOptions().find(option => option.id === this.activeId()));
  protected readonly activeField = computed(() => this.activeOption() ?? this.sortField()(this.activeId()));
  private readonly activeSortIsRandom = computed(() => this.activeField().kind === 'random');
  protected readonly activeSortCanToggle = computed(() =>
    this.sortOptions().length === 0 || (this.activeOption()?.directions.length ?? 0) > 1);
  protected readonly activeSortHasSecondaryAction = computed(() =>
    this.activeSortIsRandom() || this.activeSortCanToggle());
  protected readonly secondarySortActionLabelKey = computed(() => {
    if (this.activeSortIsRandom()) {
      return 'browse.toolbar.shuffleAgain';
    }
    return this.activeDirection() === 'asc'
      ? 'browse.toolbar.sortDescending'
      : 'browse.toolbar.sortAscending';
  });
  protected readonly activeSortIcon = computed(() =>
    browseSortDirectionIcon(this.activeField().kind, this.activeDirection()));

  protected onSortMenuOpened(): void {
    this.moreSortsExpanded.set(this.moreOptions().some(option => option.id === this.activeId()));
  }

  protected sortRankFor(option: BrowseSortOption<Key>): string {
    if (!this.multiSortActive()) {
      return '';
    }
    const index = this.sortTerms().findIndex(term => term.key === option.id);
    return index === -1 ? '' : `${index + 1}`;
  }

  protected directionIconFor(option: BrowseSortOption<Key>): LucideIconData | undefined {
    if (this.multiSortActive() || option.id !== this.activeId()) {
      return undefined;
    }
    return this.activeSortIcon();
  }

  protected onSelect(option: BrowseSortOption<Key>): void {
    const reselected = !this.multiSortActive() && option.id === this.activeId();
    const direction = reselected
      ? this.otherDirection(option) ?? option.defaultDirection
      : option.defaultDirection;
    this.sortTermsChange.emit([{key: option.id, direction}]);
  }

  protected activateSecondarySortAction(): void {
    if (this.activeSortIsRandom()) {
      this.shuffleRequested.emit();
      return;
    }
    const nextDirection = this.otherDirection(this.activeOption());
    if (nextDirection) {
      this.sortTermsChange.emit([{key: this.activeId(), direction: nextDirection}, ...this.sortTerms().slice(1)]);
    }
  }

  protected async openMultiSort(): Promise<void> {
    const ref = await this.dialogLauncher.openMultiSortDialog({
      terms: this.sortTerms(),
      options: this.sortOptions(),
      sortField: this.sortField(),
      saveDefaultFor: this.saveDefaultFor(),
    });
    ref?.onClose.pipe(take(1)).subscribe((result?: BrowseMultiSortDialogResult<Key>) => {
      if (!result) {
        return;
      }
      this.sortTermsChange.emit(result.terms);
      if (result.saveAsDefault) {
        this.sortDefaultSaved.emit(result.terms);
      }
    });
  }

  private otherDirection(option: BrowseSortOption<Key> | undefined): BrowseSortDirection | undefined {
    return option?.directions.find(direction => direction !== this.activeDirection());
  }
}
