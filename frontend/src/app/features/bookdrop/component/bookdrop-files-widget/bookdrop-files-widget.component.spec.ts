import {provideZonelessChangeDetection} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {BehaviorSubject} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {BookdropFileNotification, BookdropFileService} from '../../service/bookdrop-file.service';
import {BookdropFilesWidgetComponent} from './bookdrop-files-widget.component';

describe('BookdropFilesWidgetComponent', () => {
  let fixture: ComponentFixture<BookdropFilesWidgetComponent>;
  let summary$: BehaviorSubject<BookdropFileNotification>;

  beforeEach(async () => {
    summary$ = new BehaviorSubject<BookdropFileNotification>({
      pendingCount: 2,
      totalCount: 4,
      lastUpdatedAt: '2026-03-26T00:00:00Z',
    });

    await TestBed.configureTestingModule({
      imports: [BookdropFilesWidgetComponent, getTranslocoModule()],
      providers: [
        provideZonelessChangeDetection(),
        {
          provide: BookdropFileService,
          useValue: {summary$},
        },
        {
          provide: Router,
          useValue: {navigate: vi.fn()},
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookdropFilesWidgetComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders pending-count updates from live summary notifications', async () => {
    expect(fixture.nativeElement.querySelector('.widget-count').textContent.trim()).toBe('2');

    summary$.next({
      pendingCount: 5,
      totalCount: 7,
      lastUpdatedAt: '2026-03-26T00:01:00Z',
    });
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('.widget-count').textContent.trim()).toBe('5');
  });
});
