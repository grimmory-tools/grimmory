import {Component, computed, inject, input, linkedSignal} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideChevronLeft, LucideChevronRight} from '@lucide/angular';
import {barX, defineChart} from '@tanstack/charts';
import {scaleLinear} from '@tanstack/charts/scales/linear';

import {AppButtonComponent} from '../../../../../../shared/ui/button/app-button.component';
import {STATS_CARD} from '../../../../card/stats-context';
import {type StatsGenreTime} from '../../../../data/stats-response.models';
import {shorten, STATS_CHART_PALETTE, statsAxis, statsBar, statsBarBand, statsTooltip} from '../../../shared/stats-chart-style';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';

const PAGE_SIZE = 12;

@Component({
  selector: 'app-reading-by-genre-chart',
  imports: [AppButtonComponent, LucideChevronLeft, LucideChevronRight, StatsChartPlotComponent, TranslocoPipe],
  template: `
    <app-stats-chart-plot [options]="chart()" />

    @if (card.state() !== 'empty') {
      <nav class="flex items-center justify-center gap-3 text-xs tabular-nums text-text-secondary">
        <app-button
          variant="ghost"
          size="sm"
          iconOnly
          [ariaLabel]="'common.back' | transloco"
          [disabled]="page() === 0"
          (clicked)="page.set(page() - 1)">
          <svg lucideChevronLeft aria-hidden="true"></svg>
        </app-button>
        <span>{{ text.t('pageInfo', {current: page() + 1, total: totalPages()}) }}</span>
        <app-button
          variant="ghost"
          size="sm"
          iconOnly
          [ariaLabel]="'common.next' | transloco"
          [disabled]="page() + 1 >= totalPages()"
          (clicked)="page.set(page() + 1)">
          <svg lucideChevronRight aria-hidden="true"></svg>
        </app-button>
      </nav>
    }
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class ReadingByGenreChartComponent {
  protected readonly card = inject(STATS_CARD);
  protected readonly text = injectStatsText('statsUser.readingByGenre');

  readonly data = input.required<readonly StatsGenreTime[]>();

  readonly page = linkedSignal({source: this.data, computation: () => 0});
  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.data().length / PAGE_SIZE)));

  readonly chart = computed(() => {
    const {t} = this.text;
    const rows = this.data();
    const start = this.page() * PAGE_SIZE;
    const genres = rows.slice(start, start + PAGE_SIZE).map((row) => ({...row, hours: row.seconds / 3600}));

    return {
      ariaLabel: t('title'),
      definition: defineChart({
        chart: ({width}) => ({
          marks: [
            barX(genres.filter(({hours}) => hours > 0), {x: 'hours', y: 'genre', fill: STATS_CHART_PALETTE[0], ...statsBar(width)}),
          ],
          scales: {
            x: {
              scale: scaleLinear().domain([0, Math.max(1, ...rows.map((row) => row.seconds)) / 3600]),
              grid: true,
              axis: statsAxis(t('readingTime'), {format: (hours: number) => this.text.duration(hours * 60)}),
            },
            y: {
              scale: statsBarBand(genres.map(({genre}) => genre)),
              axis: statsAxis(t('axisGenres'), {format: (genre: string) => shorten(genre, 29)}),
            },
          },
        }),
        focus: 'nearest-y',
        focusRing: false,
        svgAnimation: true,
        tooltip: statsTooltip<(typeof genres)[number]>(([{datum: row}]) => ({
          title: row.genre,
          rows: [{label: t('readingTime'), value: this.text.duration(row.seconds / 60)}],
        })),
      }),
    };
  });
}
