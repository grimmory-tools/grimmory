import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {ReaderBookMetadataDialogComponent} from './metadata-dialog.component';

describe('ReaderBookMetadataDialogComponent', () => {
  let fixture: ComponentFixture<ReaderBookMetadataDialogComponent>;
  let component: ReaderBookMetadataDialogComponent;
  let urlHelperService: {getCoverUrl: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    urlHelperService = {
      getCoverUrl: vi.fn(() => '/covers/12?v=1'),
    };

    await TestBed.configureTestingModule({
      imports: [ReaderBookMetadataDialogComponent, getTranslocoModule()],
      providers: [{provide: UrlHelperService, useValue: urlHelperService}],
    }).compileComponents();

    fixture = TestBed.createComponent(ReaderBookMetadataDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('exposes metadata and builds a cover url when a book id is available', () => {
    component.book = {
      id: 12,
      metadata: {
        title: 'Atlas',
        coverUpdatedOn: '2024-01-02',
      },
    } as never;

    expect(component.metadata?.title).toBe('Atlas');
    expect(component.bookCoverUrl).toBe('/covers/12?v=1');
    expect(urlHelperService.getCoverUrl).toHaveBeenCalledWith(12, '2024-01-02');
  });

  it('formats missing and present metadata fields safely', () => {
    expect(component.formatAuthors(undefined)).toBeTruthy();
    expect(component.formatAuthors(['One', 'Two'])).toBe('One, Two');
    expect(component.formatFileSize(undefined)).toBeTruthy();
    expect(component.formatFileSize(512)).toBe('512.0 KB');
    expect(component.formatFileSize(2048)).toBe('2.00 MB');
    expect(component.formatDate(undefined)).toBeTruthy();
    expect(component.formatDate('2024-06-15')).toContain('2024');
  });

  it('returns the raw date when parsing falls back after an invalid date string', () => {
    expect(component.formatDate('not-a-date')).toBe('Invalid Date');
  });
});
