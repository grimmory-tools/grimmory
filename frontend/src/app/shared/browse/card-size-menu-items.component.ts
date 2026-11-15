import {booleanAttribute, ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideMinus, LucidePlus} from '@lucide/angular';

import {AppMenuItemComponent} from '../ui/menu/app-menu-item.component';
import {type GridDensityDirection} from '../util/grid-density.util';

@Component({
  selector: 'app-browse-card-size-menu-items',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {class: 'contents'},
  imports: [TranslocoPipe, AppMenuItemComponent],
  template: `
    <div class="flex min-h-8 w-full select-none items-center gap-2 pl-2 pr-0 text-sm leading-5 text-text pointer-coarse:min-h-11 pointer-coarse:pl-3">
      <span class="min-w-0 flex-1 truncate">{{ 'browse.toolbar.density' | transloco }}</span>
      <app-menu-item
        value="smaller-cards"
        [class]="stepperItemClass"
        [icon]="smallerIcon"
        [disabled]="smallerDisabled()"
        [closeOnSelect]="false"
        [attr.aria-label]="'browse.toolbar.smallerCards' | transloco"
        (selected)="sizeChange.emit('smaller')" />
      <app-menu-item
        value="larger-cards"
        [class]="stepperItemClass"
        [icon]="largerIcon"
        [disabled]="largerDisabled()"
        [closeOnSelect]="false"
        [attr.aria-label]="'browse.toolbar.largerCards' | transloco"
        (selected)="sizeChange.emit('larger')" />
    </div>
  `,
})
export class BrowseCardSizeMenuItemsComponent {
  readonly smallerDisabled = input(false, {transform: booleanAttribute});
  readonly largerDisabled = input(false, {transform: booleanAttribute});
  readonly sizeChange = output<GridDensityDirection>();

  protected readonly stepperItemClass =
    'w-10! flex-none justify-center px-0! text-text-muted pointer-coarse:w-12! [&_[data-menu-label]]:hidden';
  protected readonly smallerIcon = LucideMinus.icon;
  protected readonly largerIcon = LucidePlus.icon;
}
