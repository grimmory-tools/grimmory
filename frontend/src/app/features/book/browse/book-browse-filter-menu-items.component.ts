import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';

import {AppMenuComponent} from '../../../shared/ui/menu/app-menu.component';
import {AppMenuCheckboxComponent} from '../../../shared/ui/menu/app-menu-checkbox.component';
import {AppMenuItemComponent} from '../../../shared/ui/menu/app-menu-item.component';
import {AppMenuRadioComponent} from '../../../shared/ui/menu/app-menu-radio.component';
import {AppMenuRadioGroupComponent} from '../../../shared/ui/menu/app-menu-radio-group.component';
import {AppMenuSectionComponent} from '../../../shared/ui/menu/app-menu-section.component';

@Component({
  selector: 'app-book-browse-filter-menu-items',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {class: 'contents'},
  imports: [
    TranslocoPipe,
    AppMenuComponent,
    AppMenuCheckboxComponent,
    AppMenuItemComponent,
    AppMenuRadioComponent,
    AppMenuRadioGroupComponent,
    AppMenuSectionComponent,
  ],
  template: `
    <app-menu-section>{{ 'browse.toolbar.filters' | transloco }}</app-menu-section>
    <ng-content />
    <app-menu-checkbox
      value="exclude-on-tick"
      [checked]="excludeOnTick()"
      (selected)="excludeOnTickChange.emit(!excludeOnTick())">
      {{ 'browse.toolbar.tickToExclude' | transloco }}
    </app-menu-checkbox>
    <app-menu-item
      value="filter-logic"
      [submenu]="filterLogicMenu"
      [trailingText]="(matchAll() ? 'browse.toolbar.all' : 'browse.toolbar.any') | transloco">
      {{ 'browse.toolbar.filterLogic' | transloco }}
    </app-menu-item>
    <app-menu #filterLogicMenu="ngMenu" [ariaLabel]="'browse.toolbar.filterLogic' | transloco">
      <app-menu-radio-group [value]="matchAll() ? 'all' : 'any'" (valueSelected)="matchAllChange.emit($event === 'all')">
        <app-menu-radio value="any">{{ 'browse.toolbar.matchAny' | transloco }}</app-menu-radio>
        <app-menu-radio value="all">{{ 'browse.toolbar.matchAll' | transloco }}</app-menu-radio>
      </app-menu-radio-group>
    </app-menu>
  `,
})
export class BookBrowseFilterMenuItemsComponent {
  readonly matchAll = input.required<boolean>();
  readonly excludeOnTick = input.required<boolean>();
  readonly matchAllChange = output<boolean>();
  readonly excludeOnTickChange = output<boolean>();
}
