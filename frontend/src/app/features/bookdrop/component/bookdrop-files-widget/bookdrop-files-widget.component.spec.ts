import {provideZonelessChangeDetection, signal, WritableSignal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {BookdropFileNotification, BookdropFileService} from '../../service/bookdrop-file.service';
import {BookdropFilesWidgetComponent} from './bookdrop-files-widget.component';

describe('BookdropFilesWidgetComponent', () => {
  let fixture: ComponentFixture<BookdropFilesWidgetComponent>;
  let component: BookdropFilesWidgetComponent;
  let summary: WritableSignal<BookdropFileNotification>;
  let router: {navigate: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    summary = signal<BookdropFileNotification>({
      pendingCount: 0,
      totalCount: 0,
    });
    router = {
      navigate: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [BookdropFilesWidgetComponent, getTranslocoModule()],
      providers: [
        provideZonelessChangeDetection(),
        {provide: BookdropFileService, useValue: {summary}},
        {provide: Router, useValue: router},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookdropFilesWidgetComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('updates the visible pending count when the summary signal changes', async () => {
    summary.set({
      pendingCount: 3,
      totalCount: 9,
      lastUpdatedAt: '2026-05-28T12:05:00Z',
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(countText()).toBe('3');
    expect(fixture.nativeElement.querySelector('.widget-date')).not.toBeNull();
  });

  it('navigates to bookdrop review with a reload timestamp', () => {
    component.openReviewDialog();

    expect(router.navigate).toHaveBeenCalledWith(['/bookdrop'], {
      queryParams: {reload: expect.any(Number)},
    });
  });

  function countText(): string {
    return fixture.nativeElement.querySelector('.widget-count').textContent.trim();
  }
});
