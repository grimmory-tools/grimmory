import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideMinus, LucidePlus, type LucideIconData} from '@lucide/angular';

import {AppMenuItemComponent} from '../ui/menu/app-menu-item.component';
import {type GridDensityDirection} from '../util/grid-density.util';

@Component({
  selector: 'app-browse-density-menu-items',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'flex min-h-8 w-full select-none items-center gap-2 pl-2 pr-0 text-sm leading-5 text-text pointer-coarse:min-h-11 pointer-coarse:pl-3',
  },
  imports: [TranslocoPipe, AppMenuItemComponent],
  template: `
    <span class="min-w-0 flex-1 truncate">{{ 'browse.toolbar.density' | transloco }}</span>
    <app-menu-item
      value="smaller-cards"
      [class]="stepperItemClass"
      [icon]="smallerIcon"
      [disabled]="smallerDisabled()"
      [closeOnSelect]="false"
      [attr.aria-label]="'browse.toolbar.smallerCards' | transloco"
      (selected)="densityChange.emit('smaller')" />
    <app-menu-item
      value="larger-cards"
      [class]="stepperItemClass"
      [icon]="largerIcon"
      [disabled]="largerDisabled()"
      [closeOnSelect]="false"
      [attr.aria-label]="'browse.toolbar.largerCards' | transloco"
      (selected)="densityChange.emit('larger')" />
  `,
})
export class BrowseDensityMenuItemsComponent {
  readonly smallerDisabled = input.required<boolean>();
  readonly largerDisabled = input.required<boolean>();
  readonly densityChange = output<GridDensityDirection>();

  protected readonly stepperItemClass =
    'w-10! flex-none justify-center px-0! text-text-muted pointer-coarse:w-12! [&_[data-menu-label]]:hidden';
  protected readonly smallerIcon: LucideIconData = LucideMinus.icon;
  protected readonly largerIcon: LucideIconData = LucidePlus.icon;
}
