import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {describe, expect, it} from 'vitest';

import {ReadingSurvivalChartComponent} from './reading-survival-chart.component';
import {BookService} from '../../../../../book/service/book.service';
import {TranslocoService} from '@jsverse/transloco';
import {Book} from '../../../../../book/model/book.model';

describe('ReadingSurvivalChartComponent', () => {
  function createComponent(books: Book[], loading = false) {
    const booksSignal = signal(books);
    const loadingSignal = signal(loading);

    TestBed.configureTestingModule({
      imports: [ReadingSurvivalChartComponent],
      providers: [
        {provide: BookService, useValue: {books: booksSignal.asReadonly(), isBooksLoading: loadingSignal.asReadonly()}},
        {provide: TranslocoService, useValue: {translate: (key: string, params?: Record<string, unknown>) => params ? `${key}:${Object.values(params).join(':')}` : key}},
      ]
    });

    return TestBed.createComponent(ReadingSurvivalChartComponent).componentInstance;
  }

  it('returns empty metrics while books are loading', () => {
    const component = createComponent([], true);

    expect(component.totalStarted()).toBe(0);
    expect(component.completionRate()).toBe(0);
    expect(component.medianDropout()).toBe('');
    expect(component.dangerZoneRange()).toBe('');
    expect(component.dangerZoneDrop()).toBe('');
    expect(component.chartData()).toEqual({labels: [], datasets: []});
  });

  it('returns empty metrics when no books have been started', () => {
    const component = createComponent([
      {id: 1, libraryId: 1, libraryName: 'Alpha', pdfProgress: {page: 1, percentage: 0}} as Book,
      {id: 2, libraryId: 1, libraryName: 'Alpha', epubProgress: {cfi: 'cfi', percentage: 0}} as Book,
    ]);

    expect(component.totalStarted()).toBe(0);
    expect(component.completionRate()).toBe(0);
    expect(component.chartData()).toEqual({labels: [], datasets: []});
  });

  it('calculates survival metrics from all progress sources', () => {
    const component = createComponent([
      {id: 1, libraryId: 1, libraryName: 'Alpha', pdfProgress: {page: 1, percentage: 5}} as Book,
      {id: 2, libraryId: 1, libraryName: 'Alpha', epubProgress: {cfi: 'cfi', percentage: 15}} as Book,
      {id: 3, libraryId: 1, libraryName: 'Alpha', cbxProgress: {page: 1, percentage: 35}} as Book,
      {id: 4, libraryId: 1, libraryName: 'Alpha', koreaderProgress: {percentage: 80}} as Book,
      {id: 5, libraryId: 1, libraryName: 'Alpha', koboProgress: {percentage: 100}} as Book,
    ]);

    expect(component.totalStarted()).toBe(5);
    expect(component.completionRate()).toBe(20);
    expect(component.medianDropout()).toBe('25-50%');
    expect(component.dangerZoneRange()).toBe('0-10%');
    expect(component.dangerZoneDrop()).toBe('-20%');
    expect(component.chartData().labels).toEqual(['0%', '10%', '25%', '50%', '75%', '90%', '100%']);
    expect(component.chartData().datasets[0]?.label).toBe('statsUser.readingSurvival.survivalRate');
  });
});
