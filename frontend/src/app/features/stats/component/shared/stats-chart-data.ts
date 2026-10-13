import {cumulative} from '@tanstack/charts/transform/cumulative';
import {setISOWeek, startOfISOWeek} from 'date-fns';

import {type StatsActivityMonth} from '../../data/stats-response.models';

export interface RunningMonth {
  readonly month: string;
  readonly added: number;
  readonly finished: number;
  readonly totalAdded: number;
  readonly totalFinished: number;
  readonly backlog: number;
}

export function firstMaximum<T>(rows: readonly T[], value: (row: T) => number): T | null {
  return rows.reduce<T | null>((best, row) => (best === null || value(row) > value(best) ? row : best), null);
}

export function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

export function isoWeekStart(year: number, week: number): Date {
  return startOfISOWeek(setISOWeek(new Date(year, 6, 1), week));
}

export function shiftMonth(month: string, months: number): string {
  const index = Number(month.slice(0, 4)) * 12 + Number(month.slice(5, 7)) - 1 + months;
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`;
}

export function runningActivity(rows: readonly StatsActivityMonth[]): readonly RunningMonth[] {
  if (rows.length === 0) return [];

  const byMonth = new Map(rows.map((row) => [row.month, row]));
  const months: StatsActivityMonth[] = [];
  const last = currentMonth();
  for (let month = rows[0].month; month <= last; month = shiftMonth(month, 1)) {
    months.push(byMonth.get(month) ?? {month, added: 0, finished: 0, finishedPages: 0, backlogChange: 0});
  }
  return cumulative(months, {
    outputs: {
      totalAdded: {value: 'added', reduce: 'sum'},
      totalFinished: {value: 'finished', reduce: 'sum'},
      backlog: {value: 'backlogChange', reduce: 'sum'},
    },
  });
}
