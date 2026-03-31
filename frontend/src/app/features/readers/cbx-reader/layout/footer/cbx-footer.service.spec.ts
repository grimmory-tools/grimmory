import {describe, expect, it} from 'vitest';

import {CbxFooterService} from './cbx-footer.service';
import {Book} from '../../../../book/model/book.model';

describe('CbxFooterService', () => {
  const previousBook = {
    id: 1,
    libraryId: 11,
    libraryName: 'Library',
    metadata: {bookId: 1, title: 'Previous'},
  } as Book;

  const nextBook = {
    id: 2,
    libraryId: 11,
    libraryName: 'Library',
    metadata: {bookId: 2, title: 'Next'},
  } as Book;

  it('updates footer state and resets to the default values', () => {
    const service = new CbxFooterService();

    service.setForceVisible(true);
    service.setCurrentPage(4);
    service.setTotalPages(10);
    service.setTwoPageView(true);
    service.setSeriesBooks(previousBook, nextBook);
    service.setHasSeries(true);

    expect(service.forceVisible()).toBe(true);
    expect(service.state()).toEqual({
      currentPage: 4,
      totalPages: 10,
      isTwoPageView: true,
      previousBookInSeries: previousBook,
      nextBookInSeries: nextBook,
      hasSeries: true
    });

    service.reset();

    expect(service.forceVisible()).toBe(false);
    expect(service.state()).toEqual({
      currentPage: 0,
      totalPages: 0,
      isTwoPageView: false,
      previousBookInSeries: null,
      nextBookInSeries: null,
      hasSeries: false
    });
  });

  it('emits all footer navigation actions', () => {
    const service = new CbxFooterService();
    const previousPageEvents: void[] = [];
    const nextPageEvents: void[] = [];
    const goToPageEvents: number[] = [];
    const firstPageEvents: void[] = [];
    const lastPageEvents: void[] = [];
    const previousBookEvents: void[] = [];
    const nextBookEvents: void[] = [];
    const sliderChangeEvents: number[] = [];

    service.previousPage$.subscribe(() => previousPageEvents.push(undefined));
    service.nextPage$.subscribe(() => nextPageEvents.push(undefined));
    service.goToPage$.subscribe(value => goToPageEvents.push(value));
    service.firstPage$.subscribe(() => firstPageEvents.push(undefined));
    service.lastPage$.subscribe(() => lastPageEvents.push(undefined));
    service.previousBook$.subscribe(() => previousBookEvents.push(undefined));
    service.nextBook$.subscribe(() => nextBookEvents.push(undefined));
    service.sliderChange$.subscribe(value => sliderChangeEvents.push(value));

    service.emitPreviousPage();
    service.emitNextPage();
    service.emitGoToPage(7);
    service.emitFirstPage();
    service.emitLastPage();
    service.emitPreviousBook();
    service.emitNextBook();
    service.emitSliderChange(8);

    expect(previousPageEvents).toHaveLength(1);
    expect(nextPageEvents).toHaveLength(1);
    expect(goToPageEvents).toEqual([7]);
    expect(firstPageEvents).toHaveLength(1);
    expect(lastPageEvents).toHaveLength(1);
    expect(previousBookEvents).toHaveLength(1);
    expect(nextBookEvents).toHaveLength(1);
    expect(sliderChangeEvents).toEqual([8]);
  });
});
