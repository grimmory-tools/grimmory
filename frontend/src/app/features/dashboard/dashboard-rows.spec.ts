import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {convertToParamMap, provideRouter, RedirectCommand, type ActivatedRouteSnapshot, type RouterStateSnapshot} from '@angular/router';
import {describe, expect, it} from 'vitest';

import {type BookFileResponse, type BookSummary} from '../book/data/book-response.models';
import {
  dashboardContinueFile,
  dashboardRows,
  dashboardRowScopeResolver,
} from './dashboard-rows';
import {type DashboardConfig, type ScrollerConfig, ScrollerType} from './models/dashboard-config.model';
import {UserService} from '../settings/user-management/user.service';

function scroller(id: string, type: ScrollerType, extra: Partial<ScrollerConfig> = {}): ScrollerConfig {
  return {id, type, enabled: true, order: Number(id), ...extra};
}

function config(...scrollers: ScrollerConfig[]): DashboardConfig {
  return {scrollers};
}

function file(bookId: number, id: number, bookType: 'EPUB' | 'PDF' | 'AUDIOBOOK'): BookFileResponse {
  return {id, bookId, book: true, folderBased: false, bookType};
}

describe('dashboardRows', () => {
  it('links library rows with their own sort only, so the page otherwise keeps its saved sort', () => {
    const rows = dashboardRows(config(
      scroller('1', ScrollerType.LIBRARY, {libraryId: 3, sortField: 'addedOn', sortDirection: 'desc'}),
      scroller('2', ScrollerType.LIBRARY, {libraryId: 4}),
      scroller('3', ScrollerType.LIBRARY),
    ));

    expect(rows.map(row => [row.id, row.link?.queryParams])).toEqual([['1', {sort: '-addedOn'}], ['2', null]]);
  });

  it('builds each preset row from its filters and sort, and gives continue rows no link', () => {
    const rows = dashboardRows(config(
      scroller('1', ScrollerType.LAST_READ),
      scroller('2', ScrollerType.RECENTLY_FINISHED),
      scroller('3', ScrollerType.RANDOM),
    ));

    expect(rows.map(row => ({id: row.id, link: row.link, facets: row.scope.facets, sort: row.sort}))).toEqual([
      {
        id: '1',
        link: null,
        facets: {
          read_status: {include: ['READING', 'RE_READING', 'PAUSED'], exclude: []},
          file_type: {include: expect.not.arrayContaining(['AUDIOBOOK']), exclude: []},
        },
        sort: [{key: 'lastReadTime', direction: 'desc'}],
      },
      {
        id: '2',
        link: {route: ['/dashboard', '2', 'books'], queryParams: {sort: '-dateFinished'}},
        facets: {read_status: {include: ['READ'], exclude: []}},
        sort: [{key: 'dateFinished', direction: 'desc'}],
      },
      {
        id: '3',
        link: {route: ['/dashboard', '3', 'books'], queryParams: {sort: 'random'}},
        facets: {read_status: {include: [], exclude: ['READ', 'PARTIALLY_READ', 'READING', 'PAUSED', 'WONT_READ', 'ABANDONED']}},
        sort: [{key: 'random', direction: 'asc'}],
      },
    ]);
  });
});

describe('dashboardRowScopeResolver', () => {
  it('resolves a linked row to its scope and sends unknown and continue rows back to the dashboard', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: UserService,
          useValue: {currentUser: signal({userSettings: {dashboardConfig: config(
            scroller('4', ScrollerType.RANDOM),
            scroller('5', ScrollerType.LAST_LISTENED),
          )}})},
        },
      ],
    });
    const resolve = (rowId: string) => TestBed.runInInjectionContext(() =>
      dashboardRowScopeResolver({paramMap: convertToParamMap({rowId})} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

    expect(resolve('4')).toMatchObject({kind: 'dashboardRow', titleKey: 'settingsView.dashboardScrollers.scrollerTypes.random'});
    expect(resolve('5')).toBeInstanceOf(RedirectCommand);
    expect(resolve('missing')).toBeInstanceOf(RedirectCommand);
  });
});

describe('dashboardContinueFile', () => {
  it('picks the started file among the allowed formats, or none', () => {
    const book: BookSummary = {
      id: 1,
      libraryId: 1,
      libraryName: 'Library',
      primaryFile: file(1, 10, 'EPUB'),
      alternativeFormats: [file(1, 11, 'AUDIOBOOK')],
      audiobookProgress: {positionMs: 10, trackIndex: 0, trackPositionMs: 10, percentage: 55},
    };

    expect(dashboardContinueFile(book, ['AUDIOBOOK'])?.id).toBe(11);
    expect(dashboardContinueFile(book, ['EPUB'])).toBeUndefined();
  });
});
