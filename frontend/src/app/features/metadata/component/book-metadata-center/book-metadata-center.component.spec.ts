import {signal, type WritableSignal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap, Router} from '@angular/router';
import {queryOptions} from '@tanstack/angular-query-experimental';
import {BehaviorSubject, Subject} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {createQueryClientHarness} from '../../../../core/testing/query-testing';
import {AppSettings, type MetadataPersistenceSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {BookMetadataHostService} from '../../../../shared/service/book-metadata-host.service';
import {Book} from '../../../book/model/book.model';
import {BookService} from '../../../book/service/book.service';
import {UserService} from '../../../settings/user-management/user.service';
import {BookMetadataCenterComponent} from './book-metadata-center.component';

describe('BookMetadataCenterComponent', () => {
  let appSettingsSignal: WritableSignal<AppSettings | null>;
  let currentUserSignal: WritableSignal<{permissions?: {admin?: boolean; canEditMetadata?: boolean}} | null>;
  let queryParamMapSubject: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let routerNavigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    const queryClientHarness = createQueryClientHarness();

    appSettingsSignal = signal<AppSettings | null>(null);
    currentUserSignal = signal({permissions: {admin: true, canEditMetadata: true}});
    queryParamMapSubject = new BehaviorSubject(convertToParamMap({}));
    routerNavigate = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        ...queryClientHarness.providers,
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: new BehaviorSubject(convertToParamMap({bookId: '1'})),
            queryParamMap: queryParamMapSubject.asObservable(),
          }
        },
        {provide: Router, useValue: {navigate: routerNavigate}},
        {
          provide: BookService,
          useValue: {
            bookDetailQueryOptions: (bookId: number) => queryOptions({
              queryKey: ['books', 'detail', bookId, true],
              queryFn: async (): Promise<Book> => ({id: bookId} as Book),
            }),
            bookRecommendationsQueryOptions: (bookId: number) => queryOptions({
              queryKey: ['books', 'recommendations', bookId],
              queryFn: async () => [],
            }),
          }
        },
        {provide: UserService, useValue: {currentUser: currentUserSignal}},
        {provide: AppSettingsService, useValue: {appSettings: appSettingsSignal}},
        {provide: BookMetadataHostService, useValue: {bookSwitches$: new Subject<number>()}},
      ],
    });
  });

  it('shows the sidecar tab only when sidecar JSON is enabled', () => {
    const component = TestBed.runInInjectionContext(() => new BookMetadataCenterComponent());
    TestBed.flushEffects();

    expect(component.canShowSidecarTab).toBe(false);

    appSettingsSignal.set(buildSettings({sidecarEnabled: true}));

    expect(component.canShowSidecarTab).toBe(true);

    appSettingsSignal.set(buildSettings({sidecarEnabled: false}));

    expect(component.canShowSidecarTab).toBe(false);
  });

  it('falls back to the view tab when the query param is invalid', () => {
    const component = TestBed.runInInjectionContext(() => new BookMetadataCenterComponent());

    queryParamMapSubject.next(convertToParamMap({tab: 'missing'}));
    component.ngOnInit();

    expect(component.tab).toBe('view');
    expect(routerNavigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: 'view'},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });

  it('falls back to the view tab when the user cannot open the requested tab', () => {
    currentUserSignal.set({permissions: {admin: false, canEditMetadata: false}});
    const component = TestBed.runInInjectionContext(() => new BookMetadataCenterComponent());

    queryParamMapSubject.next(convertToParamMap({tab: 'edit'}));
    component.ngOnInit();

    expect(component.tab).toBe('view');
    expect(routerNavigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: {tab: 'view'},
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });
});

function buildSettings({
  diskType = 'LOCAL',
  sidecarEnabled,
}: {
  diskType?: AppSettings['diskType'];
  sidecarEnabled: boolean;
}): AppSettings {
  const metadataPersistenceSettings: MetadataPersistenceSettings = {
    moveFilesToLibraryPattern: false,
    convertCbrCb7ToCbz: false,
    saveToOriginalFile: {
      epub: {enabled: false, maxFileSizeInMb: 250},
      pdf: {enabled: false, maxFileSizeInMb: 250},
      cbx: {enabled: false, maxFileSizeInMb: 250},
      audiobook: {enabled: false, maxFileSizeInMb: 1000},
    },
    sidecarSettings: {
      enabled: sidecarEnabled,
      writeOnUpdate: false,
      writeOnScan: false,
      includeCoverFile: false,
    },
  };

  return {
    diskType,
    metadataPersistenceSettings,
  } as AppSettings;
}
