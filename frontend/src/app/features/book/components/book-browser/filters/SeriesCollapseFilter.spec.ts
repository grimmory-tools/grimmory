import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {MessageService} from 'primeng/api';

import {SeriesCollapseFilter} from './SeriesCollapseFilter';
import {UserService} from '../../../../settings/user-management/user.service';
import {Book} from '../../../model/book.model';

function makeBook(id: number, seriesName?: string, seriesNumber?: number): Book {
  return {
    id,
    libraryId: 1,
    libraryName: 'Library',
    metadata: {
      bookId: id,
      title: `Book ${id}`,
      seriesName,
      seriesNumber
    }
  };
}

describe('SeriesCollapseFilter', () => {
  let userService: {currentUser: ReturnType<typeof vi.fn>; getCurrentUser: ReturnType<typeof vi.fn>; updateUserSetting: ReturnType<typeof vi.fn>};
  let messageService: {add: ReturnType<typeof vi.fn>};
  let service: SeriesCollapseFilter;

  beforeEach(() => {
    vi.useFakeTimers();
    userService = {
      currentUser: vi.fn(() => ({id: 1})),
      getCurrentUser: vi.fn(() => null),
      updateUserSetting: vi.fn()
    };
    messageService = {add: vi.fn()};

    TestBed.configureTestingModule({
      providers: [
        SeriesCollapseFilter,
        {provide: UserService, useValue: userService},
        {provide: MessageService, useValue: messageService}
      ]
    });

    service = TestBed.inject(SeriesCollapseFilter);
  });

  it('returns books unchanged when collapse is disabled or series expansion is forced', () => {
    const books = [makeBook(1), makeBook(2, 'Series', 2)];

    expect(service.collapseBooks(books, false, false)).toBe(books);
    expect(service.collapseBooks([], false, true)).toEqual([]);
    expect(service.collapseBooks(books, true, true)).toBe(books);
  });

  it('collapses series into grouped books with the lowest numbered entry first', () => {
    const books = [
      makeBook(1, 'Series', 2),
      makeBook(2, 'Series', 1),
      makeBook(3),
      makeBook(4, 'Other Series', 5)
    ];

    const collapsed = service.collapseBooks(books, false, true);

    expect(collapsed).toEqual([
      makeBook(3),
      {
        ...makeBook(2, 'Series', 1),
        seriesBooks: [books[0], books[1]],
        seriesCount: 2
      },
      {
        ...makeBook(4, 'Other Series', 5),
        seriesBooks: [books[3]],
        seriesCount: 1
      }
    ]);
  });

  it('persists context-aware and global preferences with legacy fallbacks', () => {
    const user = {
      id: 1,
      userSettings: {
        entityViewPreferences: {
          global: {
            sortKey: 'addedOn',
            sortDir: 'DESC',
            view: 'GRID',
            coverSize: 1,
            seriesCollapse: true
          },
          overrides: [
            {
              entityType: 'LIBRARY',
              entityId: 99,
              preferences: {
                sortKey: 'title',
                sortDir: 'ASC',
                view: 'TABLE',
                coverSize: 1,
                seriesCollapsed: false,
                overlayBookType: true
              }
            }
          ]
        }
      }
    };

    userService.getCurrentUser.mockReturnValue(user);

    service.setContext('LIBRARY', 99);
    expect(service.seriesCollapsed()).toBe(false);

    service.setCollapsed(true);
    vi.advanceTimersByTime(500);

    expect(userService.updateUserSetting).toHaveBeenCalledWith(1, 'entityViewPreferences', expect.objectContaining({
      overrides: expect.arrayContaining([
        expect.objectContaining({
          entityType: 'LIBRARY',
          entityId: 99,
          preferences: expect.objectContaining({
            seriesCollapsed: true
          })
        })
      ])
    }));
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
      summary: 'Preference Saved'
    }));

    userService.updateUserSetting.mockClear();
    messageService.add.mockClear();

    service.setContext(null, null);
    service.setCollapsed(false);
    vi.advanceTimersByTime(500);

    expect(userService.updateUserSetting).toHaveBeenCalledWith(1, 'entityViewPreferences', expect.objectContaining({
      global: expect.objectContaining({
        seriesCollapsed: false
      })
    }));
  });
});
