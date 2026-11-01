import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';

import {createQueryClientHarness, flushQueryAsync} from '../../../../core/testing/query-testing';
import {AuthorCommandService, type UploadAuthorPhotoVariables} from '../../data/author-command.service';
import {AuthorQueryService} from '../../data/author-query.service';
import {type AuthorPhotoResult} from '../../data/author-response.models';
import {AuthorPhotoSearchComponent} from './author-photo-search.component';
import {
  createDynamicDialogHarness,
  createMessageServiceProvider,
  createMessageServiceSpy,
} from '../../../../core/testing/dialog-testing';

describe('AuthorPhotoSearchComponent', () => {
  let searchPhotos: Mock<(query: string) => Promise<AuthorPhotoResult[]>>;
  let uploadPhoto: Mock<(variables: UploadAuthorPhotoVariables) => Promise<void>>;
  let dialogHarness: ReturnType<typeof createDynamicDialogHarness<{authorId: number; authorName?: string}>>;
  let messageService: ReturnType<typeof createMessageServiceSpy>;

  beforeEach(() => {
    searchPhotos = vi.fn<(query: string) => Promise<AuthorPhotoResult[]>>().mockResolvedValue([]);
    uploadPhoto = vi.fn<(variables: UploadAuthorPhotoVariables) => Promise<void>>().mockResolvedValue(undefined);
    dialogHarness = createDynamicDialogHarness({authorId: 9, authorName: 'Ada'});
    messageService = createMessageServiceSpy();

    TestBed.configureTestingModule({
      providers: [
        ...createQueryClientHarness().providers,
        ...dialogHarness.providers,
        {
          provide: AuthorQueryService,
          useValue: {
            photoSearch: (authorId: number, query: string) => ({
              queryKey: ['photo-search', authorId, query],
              queryFn: () => searchPhotos(query),
              retry: false,
            }),
          },
        },
        {provide: AuthorCommandService, useValue: {uploadPhotoFromUrl: () => ({mutationFn: uploadPhoto})}},
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

  it('searches for the author on open and shows the photos in order', async () => {
    searchPhotos.mockResolvedValue([
      {index: 3, url: 'three', width: 300, height: 400},
      {index: 1, url: 'one', width: 100, height: 200},
      {index: 2, url: 'two', width: 200, height: 300},
    ]);

    const component = TestBed.runInInjectionContext(() => new AuthorPhotoSearchComponent());
    component.ngOnInit();
    await flushQueryAsync();

    expect(searchPhotos).toHaveBeenCalledWith('Ada');
    expect(component.photos().map(photo => photo.index)).toEqual([1, 2, 3]);
    expect(component.hasSearched()).toBe(true);
    expect(component.searching()).toBe(false);
  });

  it('saves a selected photo, reports success, and closes the dialog', async () => {
    const component = TestBed.runInInjectionContext(() => new AuthorPhotoSearchComponent());

    component.selectAndUploadPhoto({index: 1, url: 'https://example.com/photo.jpg', width: 640, height: 480});
    await flushQueryAsync();

    expect(uploadPhoto.mock.calls[0][0]).toEqual({authorId: 9, url: 'https://example.com/photo.jpg'});
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    expect(dialogHarness.dialogRef.close).toHaveBeenCalledWith(true);
  });
});
