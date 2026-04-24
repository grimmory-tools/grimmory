import {signal, type WritableSignal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, Router} from '@angular/router';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {createQueryClientHarness} from '../../../../core/testing/query-testing';
import {AppSettings, type MetadataPersistenceSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {BookMetadataHostService} from '../../../../shared/service/book-metadata-host.service';
import {BookService} from '../../../book/service/book.service';
import {UserService} from '../../../settings/user-management/user.service';
import {BookMetadataCenterComponent} from './book-metadata-center.component';

describe('BookMetadataCenterComponent', () => {
  let appSettingsSignal: WritableSignal<AppSettings | null>;
  let currentUserSignal: WritableSignal<{permissions?: {admin?: boolean; canEditMetadata?: boolean}} | null>;

  beforeEach(() => {
    const queryClientHarness = createQueryClientHarness();

    appSettingsSignal = signal<AppSettings | null>(null);
    currentUserSignal = signal({permissions: {admin: true, canEditMetadata: true}});

    TestBed.configureTestingModule({
      providers: [
        ...queryClientHarness.providers,
        {provide: ActivatedRoute, useValue: {}},
        {provide: Router, useValue: {navigate: vi.fn()}},
        {provide: BookService, useValue: {}},
        {provide: UserService, useValue: {currentUser: currentUserSignal}},
        {provide: AppSettingsService, useValue: {appSettings: appSettingsSignal}},
        {provide: BookMetadataHostService, useValue: {}},
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
