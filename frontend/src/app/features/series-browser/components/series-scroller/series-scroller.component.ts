import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {TranslocoDirective} from '@jsverse/transloco';
import {SeriesCardComponent} from '../series-card/series-card.component';
import {SeriesSummary} from '../../model/series.model';

@Component({
  selector: 'app-series-scroller',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './series-scroller.component.html',
  styleUrls: ['./series-scroller.component.scss'],
  imports: [SeriesCardComponent, TranslocoDirective]
})
export class SeriesScrollerComponent {

  @Input({required: true}) title!: string;
  @Input({required: true}) series!: SeriesSummary[];
  @Output() seriesClick = new EventEmitter<SeriesSummary>();

  onSeriesClick(s: SeriesSummary): void {
    this.seriesClick.emit(s);
  }
}
