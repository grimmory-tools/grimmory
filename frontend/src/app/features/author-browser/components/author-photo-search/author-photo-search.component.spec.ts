import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';
import type {AuthorPhotoResult} from '../../model/author.model';
import {AuthorService} from '../../service/author.service';
import {AuthorPhotoSearchComponent} from './author-photo-search.component';
import {
  createDynamicDialogHarness,
  createMessageServiceProvider,
  createMessageServiceSpy,
} from '../../../../core/testing/dialog-testing';

describe('AuthorPhotoSearchComponent', () => {
  let searchAuthorPhotos: ReturnType<typeof vi.fn>;
  let uploadAuthorPhotoFromUrl: ReturnType<typeof vi.fn>;
  let dialogHarness: ReturnType<typeof createDynamicDialogHarness<{authorId: number; authorName?: string}>>;
  let messageService: ReturnType<typeof createMessageServiceSpy>;

  beforeEach(() => {
    searchAuthorPhotos = vi.fn(() => of([]));
    uploadAuthorPhotoFromUrl = vi.fn(() => of(undefined));
    dialogHarness = createDynamicDialogHarness({authorId: 9, authorName: 'Ada'});
    messageService = createMessageServiceSpy();

    TestBed.configureTestingModule({
      providers: [
        ...dialogHarness.providers,
        {
          provide: AuthorService,
          useValue: {
          searchAuthorPhotos,
          uploadAuthorPhotoFromUrl,
          },
        },
        createMessageServiceProvider(messageService),
        {
          provide: TranslocoService,
          useValue: {
            translate: (<T = string>(key: string) => key as T) as TranslocoService['translate'],
          },
        },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('hydrates the initial author query, performs the bootstrap search, and sorts the returned photos', () => {
    searchAuthorPhotos.mockReturnValue(of<AuthorPhotoResult[]>([
      {index: 3, url: 'three', width: 300, height: 400},
      {index: 1, url: 'one', width: 100, height: 200},
      {index: 2, url: 'two', width: 200, height: 300},
    ]));

    const component = TestBed.runInInjectionContext(() => new AuthorPhotoSearchComponent());
    component.ngOnInit();

    expect(searchAuthorPhotos).toHaveBeenCalledWith(9, 'Ada');
    expect(component.searchForm.value.query).toBe('Ada');
    expect(component.photos.map(photo => photo.index)).toEqual([1, 2, 3]);
    expect(component.hasSearched).toBe(true);
    expect(component.searching).toBe(false);
  });

  it('clears results when searching fails and resets the form state on clear', () => {
    searchAuthorPhotos.mockReturnValue(throwError(() => new Error('boom')));

    const component = TestBed.runInInjectionContext(() => new AuthorPhotoSearchComponent());
    component.ngOnInit();

    expect(component.photos).toEqual([]);
    expect(component.hasSearched).toBe(true);

    component.onClear();

    expect(component.searchForm.value.query).toBeNull();
    expect(component.photos).toEqual([]);
    expect(component.hasSearched).toBe(false);
  });

  it('uploads a selected photo, reports success, and closes the dialog', () => {
    const component = TestBed.runInInjectionContext(() => new AuthorPhotoSearchComponent());
    component.ngOnInit();

    component.selectAndUploadPhoto({index: 1, url: 'https://example.com/photo.jpg', width: 640, height: 480});

    expect(uploadAuthorPhotoFromUrl).toHaveBeenCalledWith(9, 'https://example.com/photo.jpg');
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
      summary: 'authorBrowser.editor.toast.photoUploadedSummary',
    }));
    expect(dialogHarness.dialogRef.close).toHaveBeenCalledWith(true);
  });
});
