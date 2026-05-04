import {Component, input, output} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';

export type GridDensityDirection = 'smaller' | 'larger';

@Component({
  selector: 'app-grid-density-buttons',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './grid-density-buttons.component.html',
})
export class GridDensityButtonsComponent {
  readonly smallerDisabled = input(false);
  readonly largerDisabled = input(false);
  readonly densityChange = output<GridDensityDirection>();
}
