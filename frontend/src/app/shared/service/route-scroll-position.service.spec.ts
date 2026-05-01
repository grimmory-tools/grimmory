import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, provideRouter} from '@angular/router';
import {beforeEach, describe, expect, it} from 'vitest';

import {RouteScrollPositionService} from './route-scroll-position.service';

describe('RouteScrollPositionService', () => {
  let service: RouteScrollPositionService;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideRouter([])]});
    service = TestBed.inject(RouteScrollPositionService);
  });

  it('stores and retrieves scroll positions by key', () => {
    expect(service.getPosition('books')).toBeUndefined();

    service.savePosition('books', 144);
    service.savePosition('books:library', 288);

    expect(service.getPosition('books')).toBe(144);
    expect(service.getPosition('books:library')).toBe(288);
  });

  it('keeps recently used scroll positions when the cache reaches its limit', () => {
    Array.from({length: 100}, (_, index) => service.savePosition(`route-${index}`, index));

    expect(service.getPosition('route-0')).toBe(0);

    service.savePosition('route-100', 100);

    expect(service.getPosition('route-0')).toBe(0);
    expect(service.getPosition('route-99')).toBe(99);
    expect(service.getPosition('route-1')).toBeUndefined();
    expect(service.getPosition('route-100')).toBe(100);
  });

  it('creates stable route keys regardless of parameter order', () => {
    expect(service.createKey('/books', {libraryId: '1', shelfId: '2'})).toBe('/books:libraryId=1;shelfId=2');
    expect(service.createKey('/books', {shelfId: '2', libraryId: '1'})).toBe('/books:libraryId=1;shelfId=2');
  });

  it('creates route keys from the full matched path', () => {
    const route = {
      snapshot: {
        pathFromRoot: [
          {url: []},
          {url: [{path: 'authors'}]},
          {url: [{path: '42'}]},
        ],
        params: {id: '42'},
      },
    } as unknown as ActivatedRoute;

    expect(service.keyFor(route)).toBe('authors/42:id=42');
  });

  it('encodes route key values and falls back to the path when params are empty', () => {
    expect(service.createKey('/books', {slug: 'one-two', type: 'magic&shelf'})).toBe('/books:slug=one-two;type=magic%26shelf');
    expect(service.createKey('/books', {})).toBe('/books');
  });
});
