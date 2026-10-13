import {Component, computed, input} from '@angular/core';
import {areaY, defineChart, lineY, ruleX, text} from '@tanstack/charts';
import {decorative} from '@tanstack/charts/mark/decorative';
import {scaleLinear} from '@tanstack/charts/scales/linear';
import {scalePoint} from '@tanstack/charts/scales/point';

import {countTicks, STATS_CHART_PALETTE, STATS_CHART_TEXT, STATS_COMPACT_WIDTH, statsAxis, statsTooltip} from '../../../shared/stats-chart-style';
import {currentMonth, firstMaximum, type RunningMonth, shiftMonth} from '../../../shared/stats-chart-data';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsInsight, StatsInsightListComponent} from '../../../shared/stats-insight-list.component';
import {type StatsLegendItem, StatsLegendComponent} from '../../../shared/stats-legend.component';

function monthTicks(months: readonly string[]): string[] {
  const step = [1, 3, 6, 12, 24, 60].find((candidate) => months.length / candidate <= 14) ?? 120;
  return months.filter((month) => {
    const index = Number(month.slice(0, 4)) * 12 + Number(month.slice(5, 7)) - 1;
    return index % step === 0;
  });
}

@Component({
  selector: 'app-books-added-vs-finished-chart',
  imports: [StatsChartPlotComponent, StatsInsightListComponent, StatsLegendComponent],
  template: `
    <app-stats-chart-plot [options]="chart()" />
    <app-stats-legend [items]="legend()">
      <span class="ms-auto tabular-nums text-text-muted">{{ dateRange() }}</span>
    </app-stats-legend>
    <app-stats-insight-list [items]="insights()" />
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class BooksAddedVsFinishedChartComponent {
  private readonly text = injectStatsText('statsUser.booksAddedVsFinished');

  readonly data = input.required<readonly RunningMonth[]>();

  readonly yearAgo = computed(() => {
    const month = shiftMonth(currentMonth(), -12);
    return this.data().find((row) => row.month === month) ?? null;
  });
  private readonly series = computed(() => ({
    added: {name: this.text.t('legendBooksAdded'), color: STATS_CHART_PALETTE[0]},
    finished: {name: this.text.t('legendBooksFinished'), color: STATS_CHART_PALETTE[1]},
    backlog: {name: this.text.t('legendBacklog'), color: STATS_CHART_PALETTE[2]},
  }));

  readonly legend = computed<readonly StatsLegendItem[]>(() => {
    const {added, finished, backlog} = this.series();
    return [
      {label: added.name, color: added.color, swatch: 'line'},
      {label: finished.name, color: finished.color, swatch: 'line'},
      {label: backlog.name, color: backlog.color, swatch: 'area'},
    ];
  });

  readonly dateRange = computed(() => {
    const months = this.data();
    if (months.length === 0) return '';
    const month = (key: string) => this.text.date(key, {month: 'short', year: 'numeric'});
    return `${month(months[0].month)} – ${month(months[months.length - 1].month)}`;
  });

  readonly chart = computed(() => {
    const {t, number, date} = this.text;
    const {added, finished, backlog} = this.series();
    const running = this.data();
    const yearAgo = this.yearAgo();
    const highest = Math.max(0, ...running.map((row) => Math.max(row.totalAdded, row.totalFinished + row.backlog)));

    return {
      ariaLabel: t('chartLabel'),
      definition: defineChart({
        chart: ({width}) => {
          const points = running.length <= (width < STATS_COMPACT_WIDTH ? 12 : 24);
          const marker = yearAgo ? [yearAgo.month] : [];
          return {
            marks: [
              ruleX(marker, {strokeDasharray: '4 4', strokeOpacity: 0.55}),
              decorative(text(width < STATS_COMPACT_WIDTH ? [] : marker, {
                x: (month) => month,
                y: () => highest,
                text: () => t('markerYearAgo'),
                anchor: 'end',
                dx: -6,
                fill: 'var(--color-text-secondary)',
                ...STATS_CHART_TEXT,
              })),
              decorative(areaY(running, {
                x: 'month',
                y1: 'totalFinished',
                y2: (row) => row.totalFinished + row.backlog,
                fill: backlog.color,
                fillOpacity: 0.12,
              })),
              lineY(running, {x: 'month', y: 'totalAdded', stroke: added.color, points}),
              lineY(running, {x: 'month', y: 'totalFinished', stroke: finished.color, points}),
            ],
            scales: {
              x: {
                scale: scalePoint<string>().domain(running.map(({month}) => month)).padding(0.3),
                axis: statsAxis(t('axisMonth'), {
                  values: monthTicks(running.map(({month}) => month)),
                  format: (month: string) => date(month, {month: 'short', year: '2-digit'}),
                }),
              },
              y: {
                scale: scaleLinear,
                nice: true,
                grid: true,
                axis: statsAxis(t('axisCumulativeBooks'), countTicks(highest, number)),
              },
            },
          };
        },
        focus: 'group-x',
        maxFocusDistance: Number.POSITIVE_INFINITY,
        svgAnimation: true,
        tooltip: statsTooltip<(typeof running)[number]>(([{datum: row}]) => ({
          title: date(row.month, {month: 'long', year: 'numeric'}),
          rows: [
            {label: added.name, value: number(row.totalAdded), color: added.color},
            {label: finished.name, value: number(row.totalFinished), color: finished.color},
            {label: backlog.name, value: number(row.backlog), color: backlog.color},
          ],
        })),
      }),
    };
  });

  readonly insights = computed<readonly StatsInsight[]>(() => {
    const last = this.data().at(-1);
    if (!last) return [];

    const {t, number, date} = this.text;
    const month = (row: {readonly month: string} | null) => (row ? date(row.month, {month: 'short', year: 'numeric'}) : '—');
    const peak = firstMaximum(this.data(), ({added}) => added);
    const yearAgo = this.yearAgo();

    return [
      {label: t('insightCurrentBacklog'), value: number(last.backlog)},
      ...(yearAgo
        ? [{
            label: t('insightLastTwelveMonths'),
            value: number(last.backlog - yearAgo.backlog, {signDisplay: 'exceptZero'}),
            detail: t('insightLastTwelveMonthsDetail', {from: number(yearAgo.backlog), to: number(last.backlog), month: month(yearAgo)}),
          }]
        : []),
      {label: t('legendBooksFinished'), value: number(last.totalFinished)},
      {label: t('insightMostAdded'), value: number(peak?.added ?? 0), detail: month(peak)},
    ];
  });
}
