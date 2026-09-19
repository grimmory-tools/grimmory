import {Component, booleanAttribute, input, output} from '@angular/core';

import {type BrowseFilterGroup, type BrowseFilterRangeCommit, type BrowseFilterToggle} from '../facets';
import {BrowseFilterSectionComponent} from './filter-section.component';

@Component({
  selector: 'app-browse-filter-rail',
  imports: [BrowseFilterSectionComponent],
  host: {class: 'flex flex-col gap-1 text-[13px] [overflow-anchor:none] pointer-coarse:text-sm'},
  template: `
    @for (group of groups(); track group.key) {
      <app-browse-filter-section
        [group]="group"
        [alwaysShowBoxes]="alwaysShowBoxes()"
        (toggleValue)="toggleValue.emit($event)"
        (commitRange)="commitRange.emit($event)" />
    }
  `,
})
export class BrowseFilterRailComponent<K extends string = string> {
  readonly groups = input.required<readonly BrowseFilterGroup<K>[]>();
  readonly alwaysShowBoxes = input(false, {transform: booleanAttribute});
  readonly toggleValue = output<BrowseFilterToggle<K>>();
  readonly commitRange = output<BrowseFilterRangeCommit<K>>();
}
