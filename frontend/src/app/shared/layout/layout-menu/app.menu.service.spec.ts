import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, NavigationEnd } from '@angular/router';
import { Subject } from 'rxjs';
import { MenuService } from './app.menu.service';

describe('MenuService', () => {
  it('exposes the current path from the router URL', () => {
    const routerEvents = new Subject<unknown>();

    TestBed.configureTestingModule({
      providers: [
        MenuService,
        {
          provide: Router,
          useValue: {
            url: '/dashboard?tab=recent',
            events: routerEvents.asObservable(),
          },
        },
      ],
    });

    const service = TestBed.inject(MenuService);
    expect(service.currentPath()).toBe('/dashboard');
  });

  it('updates currentPath on NavigationEnd', () => {
    const routerEvents = new Subject<unknown>();

    TestBed.configureTestingModule({
      providers: [
        MenuService,
        {
          provide: Router,
          useValue: {
            url: '/',
            events: routerEvents.asObservable(),
          },
        },
      ],
    });

    const service = TestBed.inject(MenuService);
    routerEvents.next(new NavigationEnd(1, '/library/1/books', '/library/1/books'));

    expect(service.currentPath()).toBe('/library/1/books');
  });
});
