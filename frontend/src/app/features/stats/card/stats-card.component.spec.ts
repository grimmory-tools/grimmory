import {Component, computed, input, signal} from '@angular/core';
import {HttpTestingController} from '@angular/common/http/testing';
import {type ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {
  createAuthServiceStub,
  createQueryClientHarness,
  flushQueryAsync,
} from '../../../core/testing/query-testing';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {AuthService} from '../../../shared/service/auth.service';
import {StatsCardComponent} from './stats-card.component';
import {type AnyStatsChartDefinition, defineStatsChart, type StatsChart} from './stats-chart-definition';
import {STATS_CONTEXT} from './stats-context';

const BASE = `${API_CONFIG.BASE_URL}/api/v1/stats`;

@Component({
  selector: 'app-test-chart',
  template: `<pre>{{ json() }}</pre>`,
})
class TestChartComponent<T> implements StatsChart<T> {
  readonly data = input.required<T>();
  protected readonly json = computed(() => JSON.stringify(this.data()));
}

const BOOK_FORMATS = defineStatsChart({
  id: 'library.bookFormats',
  nameKey: 'statsLibrary.chartNames.bookFormats',
  titleKey: 'statsLibrary.bookFormats.title',
  size: 'small',
  filter: 'none',
  query: (params, stats) => stats.distribution(params, 'format'),
  isEmpty: formats => formats.rows.length === 0,
  chart: TestChartComponent,
});

const SESSIONS_BY_PERIOD = defineStatsChart({
  id: 'reading.sessionActivity',
  nameKey: 'statsUser.chartNames.sessionActivity',
  titleKey: 'statsUser.sessionActivity.title',
  size: 'medium',
  filter: 'year',
  query: (params, stats) => stats.slots(params, params.year),
  select: (slots, {year}) => ({year, seconds: slots.rows.reduce((sum, slot) => sum + slot.seconds, 0)}),
  isEmpty: () => false,
  chart: TestChartComponent,
});

const TIMELINE = defineStatsChart({
  id: 'reading.sessionTimeline',
  nameKey: 'statsUser.chartNames.sessionTimeline',
  titleKey: 'statsUser.sessionTimeline.title',
  size: 'full',
  filter: 'week',
  query: (params, stats) => stats.week(params, params.year, params.week),
  isEmpty: sessions => sessions.rows.length === 0,
  chart: TestChartComponent,
});

const FORMATS_URL = `${BASE}/library/distribution/format?facet_logic=and&tz=UTC`;

function slots(seconds: number) {
  return {rows: seconds === 0 ? [] : [{weekday: 1, hour: 10, seconds}]};
}

describe('StatsCardComponent', () => {
  let http: HttpTestingController;

  function configure(): void {
    const harness = createQueryClientHarness();
    harness.queryClient.setDefaultOptions({queries: {retry: false}});

    TestBed.configureTestingModule({
      imports: [getTranslocoModule({preloadLangs: true})],
      providers: [
        ...harness.providers,
        {provide: AuthService, useValue: createAuthServiceStub()},
        {provide: STATS_CONTEXT, useValue: {scope: signal({facets: {}, facetLogic: 'and', tz: 'UTC'})}},
      ],
    });
    http = TestBed.inject(HttpTestingController);
  }

  async function render(definition: AnyStatsChartDefinition): Promise<ComponentFixture<StatsCardComponent>> {
    const fixture = TestBed.createComponent(StatsCardComponent);
    fixture.componentRef.setInput('definition', definition);
    await flushQueryAsync();
    return fixture;
  }

  function chartText(fixture: ComponentFixture<StatsCardComponent>): string | undefined {
    return (fixture.nativeElement as HTMLElement).querySelector('app-test-chart pre')?.textContent ?? undefined;
  }

  async function choose(fixture: ComponentFixture<StatsCardComponent>, label: string, option: string): Promise<void> {
    (fixture.nativeElement.querySelector(`[role="combobox"][aria-label="${label}"]`) as HTMLElement).click();
    await flushQueryAsync();
    const match = [...document.querySelectorAll<HTMLElement>('[role="option"]')].find(candidate => candidate.textContent?.trim() === option);
    if (!match) throw new Error(`Expected ${option} option`);
    match.click();
    await flushQueryAsync();
  }

  beforeEach(() => {
    vi.useFakeTimers({toFake: ['Date']});
    vi.setSystemTime(new Date(2026, 0, 2, 12));
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('shows the title without a description, then renders the chart with the selected data', async () => {
    configure();
    const fixture = await render(BOOK_FORMATS);
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('h2')?.textContent).toContain('Book Formats');
    expect(element.querySelector('h2 + p')).toBeNull();
    expect(fixture.componentInstance.state()).toBe('loading');
    expect(chartText(fixture)).toBe('{"rows":[]}');

    http.expectOne(FORMATS_URL).flush({rows: [{value: 'EPUB', books: 4}]});
    await flushQueryAsync();

    expect(fixture.componentInstance.state()).toBe('ready');
    expect(chartText(fixture)).toBe('{"rows":[{"value":"EPUB","books":4}]}');
  });

  it('keeps the previous chart, drawn with its own year, until a new year\'s data arrives', async () => {
    configure();
    const fixture = await render(SESSIONS_BY_PERIOD);
    const url = (period: string) =>
      `${BASE}/reading/slots?facet_logic=and&tz=UTC&period=${period}`;
    http.expectOne(url('2026')).flush(slots(3));
    await flushQueryAsync();
    expect(chartText(fixture)).toBe('{"year":2026,"seconds":3}');

    await choose(fixture, 'Select Year', '2025');
    const pending = http.expectOne(url('2025'));
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
    expect(chartText(fixture)).toBe('{"year":2026,"seconds":3}');

    pending.flush(slots(5));
    await flushQueryAsync();
    expect(chartText(fixture)).toBe('{"year":2025,"seconds":5}');
  });

  it('labels the last week of a 53-week ISO year and steps across the year boundary both ways', async () => {
    vi.setSystemTime(new Date(2027, 0, 1, 12));
    configure();
    const fixture = await render(TIMELINE);
    const url = (week: string) => `${BASE}/reading/week?facet_logic=and&tz=UTC&week=${week}`;
    const header = () => fixture.nativeElement.querySelector('header')?.textContent ?? '';
    http.expectOne(url('2026-W53')).flush({rows: [], truncated: false});
    await flushQueryAsync();
    expect(header()).toContain('Week 53');
    expect(header()).toContain('Dec 28 – Jan 3');

    (fixture.nativeElement.querySelector('button[aria-label="Next week"]') as HTMLButtonElement).click();
    await flushQueryAsync();
    http.expectOne(url('2027-W01')).flush({rows: [], truncated: false});
    await flushQueryAsync();
    expect(header()).toContain('Jan 4 – Jan 10');

    (fixture.nativeElement.querySelector('button[aria-label="Previous week"]') as HTMLButtonElement).click();
    await flushQueryAsync();
    (fixture.nativeElement.querySelector('button[aria-label="Previous week"]') as HTMLButtonElement).click();
    await flushQueryAsync();
    http.expectOne(url('2026-W52')).flush({rows: [], truncated: false});
    await flushQueryAsync();
    expect(header()).toContain('Week 52');
    expect(header()).toContain('Dec 21 – Dec 27');
  });
});
