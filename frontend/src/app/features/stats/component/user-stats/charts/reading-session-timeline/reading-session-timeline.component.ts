import {NgTemplateOutlet} from '@angular/common';
import {Component, computed, inject, input} from '@angular/core';
import {defineChart, rect, ruleX} from '@tanstack/charts';
import {scaleLinear} from '@tanstack/charts/scales/linear';
import {addDays, addWeeks, startOfDay} from 'date-fns';

import {UrlHelperService} from '../../../../../../shared/service/url-helper.service';
import {STATS_CARD} from '../../../../card/stats-context';
import {type StatsWeek, type StatsWeekSession} from '../../../../data/stats-response.models';
import {statsAxis, statsChartColors, statsColorScale, statsTooltip} from '../../../shared/stats-chart-style';
import {isoWeekStart} from '../../../shared/stats-chart-data';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsLegendItem, StatsLegendComponent} from '../../../shared/stats-legend.component';

export interface SessionTimelineData {
  readonly year: number;
  readonly week: number;
  readonly sessions: StatsWeek;
}

interface TimelineSegment {
  readonly key: string;
  readonly bookId: number;
  readonly bookTitle: string;
  readonly format: StatsWeekSession['format'];
  readonly dayIndex: number;
  readonly startHour: number;
  readonly endHour: number;
  readonly durationMinutes: number;
}

interface PlacedSegment extends TimelineSegment {
  readonly y1: number;
  readonly y2: number;
}

interface TimelineBlock extends PlacedSegment {
  readonly x1: number;
  readonly x2: number;
}

interface TimeWindow {
  readonly start: number;
  readonly end: number;
  readonly segments: readonly PlacedSegment[];
}

const FORMATS: readonly StatsWeekSession['format'][] = ['EPUB', 'PDF', 'CBX', 'AUDIOBOOK', 'MOBI', 'AZW3', 'FB2'];
const DAYS = [0, 1, 2, 3, 4, 5, 6];
const DAY_BOUNDARIES = [...DAYS.map((day) => day - 0.5), 6.5];
const MINIMUM_SPAN_HOURS = 2;
const MINIMUM_BLOCK_PIXELS = 10;
const BLOCK_INSET = 1;
const MARGIN = {top: 26, bottom: 8};

@Component({
  selector: 'app-reading-session-timeline',
  imports: [NgTemplateOutlet, StatsChartPlotComponent, StatsLegendComponent],
  template: `
    <div class="scroll-fade-x flex min-h-0 flex-1 overflow-x-auto pb-1">
      <div class="flex flex-1 flex-col" [class.min-w-216]="card.state() === 'ready'">
        <app-stats-chart-plot [options]="chart()" [tooltipBody]="bookTooltip" />
      </div>
    </div>
    <app-stats-legend [items]="legend()" />
    @if (truncatedNote(); as note) {
      <p class="px-2 text-xs text-text-muted">{{ note }}</p>
    }

    <ng-template #bookTooltip let-tooltip>
      <div class="flex max-w-sm items-start gap-3">
        @if (tooltip.points[0]; as point) {
          <img class="h-24 w-16 shrink-0 rounded object-cover" alt=""
            [src]="urlHelper.getDirectThumbnailUrl(point.datum.bookId)" />
        }
        <div class="min-w-52 flex-1">
          <ng-container [ngTemplateOutlet]="tooltip.defaultBody" />
        </div>
      </div>
    </ng-template>
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class ReadingSessionTimelineComponent {
  protected readonly urlHelper = inject(UrlHelperService);
  protected readonly card = inject(STATS_CARD);
  private readonly text = injectStatsText('statsUser.sessionTimeline');
  private readonly sessionText = injectStatsText('metadata.readingSessions');

  readonly data = input.required<SessionTimelineData>();

  readonly segments = computed(() => {
    const {year, week, sessions} = this.data();
    return weekSegments(sessions.rows, year, week);
  });

  private readonly window = computed(() => timeWindow(this.segments()));

  private readonly formatColors = computed(() => {
    const present = new Set(this.segments().map((segment) => segment.format));
    return statsChartColors(FORMATS.filter((format) => present.has(format)));
  });

  protected readonly truncatedNote = computed(() => {
    const {rows, truncated} = this.data().sessions;
    return truncated ? this.text.t('truncated', {count: this.text.number(rows.length)}) : null;
  });

  protected readonly legend = computed<readonly StatsLegendItem[]>(() =>
    this.formatColors().map(([format, color]) => ({label: this.formatName(format), color})),
  );

  protected readonly chart = computed(() => {
    const {t, hour, duration} = this.text;
    const window = this.window();
    const colors = this.formatColors();

    return {
      ariaLabel: t('title'),
      definition: defineChart({
        chart: ({height}) => {
          const hourPixels = (height - MARGIN.top - MARGIN.bottom) / (window.end - window.start);
          return {
            marks: [
              ruleX(DAY_BOUNDARIES, {strokeOpacity: 0.11}),
              rect(laneBlocks(window, (MINIMUM_BLOCK_PIXELS + 2 * BLOCK_INSET) / hourPixels), {
                x1: 'x1',
                x2: 'x2',
                y1: 'y1',
                y2: 'y2',
                key: 'key',
                color: 'format',
                inset: BLOCK_INSET,
                radius: 4,
              }),
            ],
            scales: {
              x: {
                scale: scaleLinear().domain([-0.5, 6.5]),
                side: 'top',
                axis: statsAxis(undefined, {values: DAYS, size: 0, format: (day: number) => this.text.weekday(day + 1)}),
              },
              y: {
                scale: scaleLinear().domain([window.start, window.end]),
                reverse: true,
                grid: true,
                axis: statsAxis(undefined, {
                  count: Math.min(12, Math.max(2, Math.ceil(window.end - window.start))),
                  format: (value: number) => hour(value),
                }),
              },
            },
            color: statsColorScale(colors),
            margin: MARGIN,
          };
        },
        svgAnimation: true,
        tooltip: statsTooltip<TimelineBlock>(([{datum: block, color}]) => ({
          title: block.bookTitle || t('readingSession'),
          rows: [
            {
              label: t('tooltipTime'),
              value: `${hour(block.startHour, '2-digit')}–${hour(block.endHour, '2-digit')}`,
              color,
            },
            {label: t('tooltipDuration'), value: duration(block.durationMinutes)},
            {label: t('tooltipFormat'), value: this.formatName(block.format)},
          ],
        })),
      }),
    };
  });

  private formatName(format: StatsWeekSession['format']): string {
    return format === 'AUDIOBOOK' ? this.sessionText.t('audio') : format;
  }
}

function weekSegments(rows: readonly StatsWeekSession[], year: number, week: number): readonly TimelineSegment[] {
  const weekStart = isoWeekStart(year, week);
  const weekEnd = addWeeks(weekStart, 1);

  return rows.flatMap((row, index) => {
    const start = new Date(row.start);
    const end = new Date(row.end);
    const segments: TimelineSegment[] = [];

    for (let cursor = start < weekStart ? weekStart : start; cursor < end && cursor < weekEnd;) {
      const midnight = startOfDay(addDays(cursor, 1));
      const until = [end, weekEnd, midnight].reduce((earliest, time) => (time < earliest ? time : earliest));
      segments.push({
        key: `${row.bookId}:${start.getTime()}:${index}:${cursor.getTime()}`,
        bookId: row.bookId,
        bookTitle: row.title,
        format: row.format,
        dayIndex: (cursor.getDay() + 6) % 7,
        startHour: hourOf(cursor),
        endHour: until.getTime() === midnight.getTime() ? 24 : hourOf(until),
        durationMinutes: (until.getTime() - cursor.getTime()) / 60_000,
      });
      cursor = until;
    }
    return segments;
  });
}

function hourOf(date: Date): number {
  return date.getHours() + date.getMinutes() / 60 + date.getSeconds() / 3600 + date.getMilliseconds() / 3_600_000;
}

function unwrap(hour: number, wrap: number, intervalEnd: boolean): number {
  return hour < wrap || (intervalEnd && hour === wrap) ? hour + 24 : hour;
}

function timeWindow(segments: readonly TimelineSegment[]): TimeWindow {
  if (segments.length === 0) return {start: 0, end: MINIMUM_SPAN_HOURS, segments: []};

  const merged: {start: number; end: number}[] = [];
  const sorted = [...segments].sort((left, right) => left.startHour - right.startHour || left.endHour - right.endHour);
  for (const {startHour: start, endHour: end} of sorted) {
    const previous = merged.at(-1);
    if (previous && start <= previous.end) previous.end = Math.max(previous.end, end);
    else merged.push({start, end});
  }

  let wrap = merged[0].start;
  let largestGap = -1;
  merged.forEach((interval, index) => {
    const last = index === merged.length - 1;
    const next = merged[last ? 0 : index + 1];
    const gap = next.start + (last ? 24 : 0) - interval.end;
    if (gap > largestGap) {
      largestGap = gap;
      wrap = next.start;
    }
  });

  const placed = segments.map((segment) => ({
    ...segment,
    y1: unwrap(segment.startHour, wrap, false),
    y2: unwrap(segment.endHour, wrap, true),
  }));
  const start = Math.floor(Math.min(...placed.map(({y1}) => y1)));
  const end = Math.ceil(Math.max(...placed.map(({y2}) => y2)));
  return {start, end: Math.max(end, start + MINIMUM_SPAN_HOURS), segments: placed};
}

function laneBlocks({end, segments}: TimeWindow, minimumHours: number): readonly TimelineBlock[] {
  const blocks = segments
    .map((segment) => {
      const y2 = Math.min(end, Math.max(segment.y2, segment.y1 + minimumHours));
      return {...segment, y1: Math.min(segment.y1, y2 - minimumHours), y2};
    })
    .sort((left, right) => left.dayIndex - right.dayIndex || left.y1 - right.y1 || right.y2 - left.y2);

  const lanes = new Map<string, number>();
  const laneCounts = new Map<string, number>();
  let cluster: string[] = [];
  let laneEnds: number[] = [];
  let clusterEnd = Number.NEGATIVE_INFINITY;
  let day = -1;
  const closeCluster = () => cluster.forEach((key) => laneCounts.set(key, laneEnds.length));

  for (const block of blocks) {
    if (block.dayIndex !== day || block.y1 >= clusterEnd) {
      closeCluster();
      cluster = [];
      laneEnds = [];
      clusterEnd = block.y2;
      day = block.dayIndex;
    }
    clusterEnd = Math.max(clusterEnd, block.y2);
    const free = laneEnds.findIndex((laneEnd) => block.y1 >= laneEnd);
    const lane = free === -1 ? laneEnds.length : free;
    laneEnds[lane] = block.y2;
    lanes.set(block.key, lane);
    cluster.push(block.key);
  }
  closeCluster();

  return blocks.map((block) => {
    const lane = lanes.get(block.key) ?? 0;
    const laneWidth = 1 / (laneCounts.get(block.key) ?? 1);
    const inset = Math.min(0.02, laneWidth * 0.1);
    const x1 = block.dayIndex - 0.5 + lane * laneWidth + inset;
    return {...block, x1, x2: x1 + laneWidth - 2 * inset};
  });
}
