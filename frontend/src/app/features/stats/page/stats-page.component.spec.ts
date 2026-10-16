import {LiveAnnouncer} from '@angular/cdk/a11y';
import {HttpTestingController} from '@angular/common/http/testing';
import {signal} from '@angular/core';
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
import {LibraryService} from '../../book/service/library.service';
import {UserService} from '../../settings/user-management/user.service';
import {STATS_PAGE, type StatsPageId} from '../card/stats-context';
import {LIBRARY_STATS_CHARTS} from '../registry/library-stats.registry';
import {READING_STATS_CHARTS} from '../registry/reading-stats.registry';
import {StatsPageComponent} from './stats-page.component';

const STATS_URL = `${API_CONFIG.BASE_URL}/api/v1/stats/`;

describe('StatsPageComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<StatsPageComponent>;
  const tz = encodeURIComponent(Intl.DateTimeFormat().resolvedOptions().timeZone).replace(/%2F/g, '/');

  async function render(page: StatsPageId): Promise<ComponentFixture<StatsPageComponent>> {
    const harness = createQueryClientHarness();
    harness.queryClient.setDefaultOptions({queries: {retry: false}});

    TestBed.configureTestingModule({
      imports: [getTranslocoModule({preloadLangs: true})],
      providers: [
        ...harness.providers,
        {provide: AuthService, useValue: createAuthServiceStub()},
        {provide: UserService, useValue: {currentUser: signal({name: 'Ada', username: 'ada'})}},
        {provide: LibraryService, useValue: {libraries: signal([{id: 2, name: 'Comics'}, {id: 1, name: 'Fiction'}])}},
        {provide: STATS_PAGE, useValue: page},
        {provide: LiveAnnouncer, useValue: {announce: vi.fn()}},
      ],
    });
    http = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(StatsPageComponent);
    await flushQueryAsync();
    return fixture;
  }

  function statsRequests() {
    return http.match(request => request.url.startsWith(STATS_URL)).map(({request}) => request);
  }

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function moveButton(chartId: string, direction: 'earlier' | 'later'): HTMLButtonElement {
    return host().querySelector(`[data-chart-move-${direction}="${chartId}"] button`) as HTMLButtonElement;
  }

  async function startEditing(): Promise<void> {
    (host().querySelector('#stats-page-config-trigger') as HTMLButtonElement).click();
    await flushQueryAsync();
    const editItem = [...document.querySelectorAll<HTMLElement>('app-menu-item')]
      .find(item => item.textContent?.trim() === 'Edit');
    if (!editItem) throw new Error('Expected edit menu item');
    editItem.click();
    await flushQueryAsync();
  }

  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    fixture?.destroy();
    localStorage.clear();
  });

  it('narrows every request to the library picked from the app library list', async () => {
    await render('library');
    const initialUrls = statsRequests().map(request => request.urlWithParams);
    expect(initialUrls).toHaveLength(LIBRARY_STATS_CHARTS.length);
    for (const url of initialUrls) {
      expect(url).toContain(`?facet_logic=and&tz=${tz}`);
      expect(url).not.toContain('facet=');
    }

    (host().querySelector('#stats-page-library') as HTMLElement).click();
    await flushQueryAsync();
    const options = [...document.querySelectorAll<HTMLElement>('[role="option"]')];
    expect(options.map(option => option.textContent?.trim())).toEqual(['All Libraries', 'Comics', 'Fiction']);
    options[1].click();
    await flushQueryAsync();
    const urls = statsRequests().map(request => request.urlWithParams);
    expect(urls).toHaveLength(LIBRARY_STATS_CHARTS.length);
    expect(urls.every(url => url.includes('&facet=library:2&'))).toBe(true);
  });

  it('restores move button focus after reordering and removal, and remembers the layout', async () => {
    await render('reading');
    statsRequests();
    await startEditing();

    moveButton('reading.personalRating', 'earlier').click();
    await flushQueryAsync();

    expect([...host().querySelectorAll('app-stats-card h2')].slice(0, 2).map(title => title.textContent?.trim()))
      .toEqual(['Personal Ratings', 'Reading Session Activity']);
    expect(TestBed.inject(LiveAnnouncer).announce).toHaveBeenCalledWith(`Personal Ratings, 1 / ${READING_STATS_CHARTS.length}`);
    expect(document.activeElement).toBe(moveButton('reading.personalRating', 'later'));

    (host().querySelector('button[aria-label="Remove Personal Ratings"]') as HTMLButtonElement).click();
    await flushQueryAsync();

    expect(host().querySelector('app-stats-card h2')?.textContent?.trim()).toBe('Reading Session Activity');
    expect(document.activeElement).toBe(moveButton('reading.sessionActivity', 'later'));
    expect(JSON.parse(localStorage.getItem('readingStatsPreferences') ?? '{}').hidden).toEqual(['reading.personalRating']);
  });
});
