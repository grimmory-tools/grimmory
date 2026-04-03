import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

import {BookShelfDragDropService} from './book-shelf-drag-drop.service';
import {BookService} from '../../service/book.service';
import {LoadingService} from '../../../../core/services/loading.service';
import {Book} from '../../model/book.model';

function makeBook(overrides: Partial<Book> = {}): Book {
  return {
    id: 7,
    libraryId: 1,
    libraryName: 'Library',
    shelves: [],
    metadata: {
      bookId: 7,
      title: 'Dragged Book'
    },
    ...overrides
  };
}

describe('BookShelfDragDropService', () => {
  let service: BookShelfDragDropService;
  let bookService: { updateBookShelves: ReturnType<typeof vi.fn> };
  let loadingService: { show: ReturnType<typeof vi.fn>; hide: ReturnType<typeof vi.fn> };
  let messageService: { add: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    bookService = {
      updateBookShelves: vi.fn(() => of([]))
    };
    loadingService = {
      show: vi.fn(() => 'loader-id'),
      hide: vi.fn()
    };
    messageService = {
      add: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        BookShelfDragDropService,
        {provide: BookService, useValue: bookService},
        {provide: LoadingService, useValue: loadingService},
        {provide: MessageService, useValue: messageService},
        {
          provide: TranslocoService,
          useValue: {
            translate: vi.fn((key: string) => `translated:${key}`)
          }
        }
      ]
    });

    service = TestBed.inject(BookShelfDragDropService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('rejects dropping onto the only existing shelf', () => {
    service.draggedBook.set(makeBook({
      shelves: [{id: 5, name: 'Keep'}]
    }));

    expect(service.canDropOnShelf(5)).toBe(false);
    expect(service.canDropOnShelf(6)).toBe(true);
  });

  it('reassigns the dragged book to the dropped shelf and removes other shelves', () => {
    const event = {
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
      dataTransfer: {dropEffect: 'none'}
    } as unknown as DragEvent;

    service.draggedBook.set(makeBook({
      shelves: [
        {id: 2, name: 'Old'},
        {id: 9, name: 'Keep'}
      ]
    }));

    service.dropOnShelf(event, 9);

    expect(bookService.updateBookShelves).toHaveBeenCalledWith(
      new Set([7]),
      new Set([9]),
      new Set([2])
    );
    expect(loadingService.hide).toHaveBeenCalledWith('loader-id');
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'info'
    }));
    expect(service.draggedBook()).toBeNull();
  });

  it('only changes the hovered shelf when entering a different valid shelf', () => {
    const firstTarget = document.createElement('div');
    const secondTarget = document.createElement('div');

    service.draggedBook.set(makeBook({
      shelves: [{id: 2, name: 'Old'}]
    }));

    service.onShelfDragEnter(firstTarget, 4);
    expect(firstTarget.classList.contains('drop-target-active')).toBe(true);

    service.onShelfDragEnter(firstTarget, 4);
    expect(firstTarget.classList.contains('drop-target-active')).toBe(true);

    service.onShelfDragEnter(secondTarget, 5);
    expect(firstTarget.classList.contains('drop-target-active')).toBe(false);
    expect(secondTarget.classList.contains('drop-target-active')).toBe(true);

    service.onShelfDragEnter(firstTarget, 2);
    expect(secondTarget.classList.contains('drop-target-active')).toBe(true);
  });

  it('clears drag state and reports an error when the shelf update fails', () => {
    bookService.updateBookShelves.mockReturnValueOnce(throwError(() => new Error('boom')));
    const event = {
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
      dataTransfer: {dropEffect: 'none'}
    } as unknown as DragEvent;

    service.draggedBook.set(makeBook({
      shelves: [{id: 2, name: 'Old'}]
    }));

    service.dropOnShelf(event, 4);

    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error'
    }));
    expect(service.draggedBook()).toBeNull();
  });
});
