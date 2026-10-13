import {Component, computed, input} from '@angular/core';
import {defineChart} from '@tanstack/charts';
import {angleGrid, focusGroupAngle, polar, radialArea, radialDot, radialGrid, radialLine} from '@tanstack/charts/polar';
import {scaleLinear} from '@tanstack/charts/scales/linear';
import {scalePoint} from '@tanstack/charts/scales/point';
import {curveLinearClosed} from 'd3-shape';

import {type ProfileRow} from '../../../../data/stats-response.models';
import {STATS_CHART_PALETTE, STATS_POLAR_GRID, statsTooltip} from '../../../shared/stats-chart-style';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsInsight, StatsInsightListComponent} from '../../../shared/stats-insight-list.component';

const PROFILES = {
  dna: {
    traits: ['adventurous', 'perfectionist', 'intellectual', 'emotional', 'patient', 'social', 'nostalgic', 'ambitious'],
    prefix: 'readingDna',
    group: 'traits',
    tooltip: 'tooltipPersonality',
  },
  habits: {
    traits: ['consistency', 'multitasking', 'completionism', 'exploration', 'organization', 'intensity', 'methodology', 'momentum'],
    prefix: 'readingHabits',
    group: 'habits',
    tooltip: 'tooltipHabit',
  },
};

const INSET = 32;
const LABEL_ROOM = 92;

export interface ReadingProfileData {
  readonly profile: ProfileRow['profile'];
  readonly rows: readonly ProfileRow[];
}

function profileLevel(score: number): 'low' | 'mid' | 'high' {
  if (score < 33) return 'low';
  return score < 67 ? 'mid' : 'high';
}

@Component({
  selector: 'app-reading-profile-chart',
  imports: [StatsChartPlotComponent, StatsInsightListComponent],
  template: `
    <app-stats-chart-plot [options]="chart()" />
    <app-stats-insight-list [items]="insights()" />
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class ReadingProfileChartComponent {
  private readonly text = injectStatsText('statsUser');

  readonly data = input.required<ReadingProfileData>();

  readonly chart = computed(() => {
    const {t, number} = this.text;
    const {prefix, group, tooltip} = PROFILES[this.data().profile];
    const axes = this.data().rows.map(({trait, score}) => ({name: t(`${prefix}.${group}.${trait}`), score}));
    const color = STATS_CHART_PALETTE[0];
    const profile = {angle: 'name', radius: 'score', curve: curveLinearClosed} as const;

    return {
      ariaLabel: t(`${prefix}.title`),
      definition: defineChart({
        chart: ({width, height}) => ({
          marks: [
            polar({
              inset: Math.min(width, height) / 2 - Math.min(height / 2 - INSET, width / 2 - LABEL_ROOM),
              scales: {
                angle: {scale: scalePoint<string>().domain(axes.map(({name}) => name)), wrap: true},
                radius: {scale: scaleLinear().domain([0, 100])},
              },
              guides: [
                radialGrid({values: [20, 40, 60, 80, 100], shape: 'circle', labels: true, ...STATS_POLAR_GRID}),
                angleGrid({labelOffset: 12, ...STATS_POLAR_GRID}),
              ],
              marks: [
                radialArea(axes, {...profile, fill: color, fillOpacity: 0.18}),
                radialLine(axes, {...profile, stroke: color, strokeWidth: 2}),
                radialDot(axes, {angle: 'name', radius: 'score', r: 5, fill: color, stroke: 'var(--color-page)', strokeWidth: 1.5}),
              ],
            }),
          ],
          scales: {x: null, y: null},
        }),
        focus: focusGroupAngle,
        svgAnimation: true,
        tooltip: statsTooltip<(typeof axes)[number]>(([{datum: axis}]) => ({
          title: t(`${prefix}.${tooltip}`, {label: axis.name}),
          rows: [{label: t(`${prefix}.tooltipScore`), value: `${number(axis.score)}/100`}],
        })),
      }),
    };
  });

  readonly insights = computed<readonly StatsInsight[]>(() => {
    const {t, number} = this.text;
    const {traits, prefix, group} = PROFILES[this.data().profile];
    const scores = new Map(this.data().rows.map(({trait, score}) => [trait, score]));
    return traits.map((trait) => {
      const score = scores.get(trait);
      return {
        label: t(`${prefix}.${group}.${trait}`),
        value: score === undefined ? '—' : number(score),
        detail: score === undefined ? '' : t(`${prefix}.descriptions.${trait}.${profileLevel(score)}`),
      };
    });
  });
}
