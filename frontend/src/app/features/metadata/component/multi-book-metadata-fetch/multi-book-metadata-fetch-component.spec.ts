import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';

import {AppSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {MetadataRefreshType} from '../../model/request/metadata-refresh-type.enum';
import {MultiBookMetadataFetchComponent} from './multi-book-metadata-fetch-component';

describe('MultiBookMetadataFetchComponent', () => {
  const appSettings = signal<AppSettings | null>(null);

  beforeEach(() => {
    appSettings.set(null);

    TestBed.configureTestingModule({
      providers: [
        {
          provide: DynamicDialogConfig,
          useValue: {
            data: {
              bookIds: [3, 5],
              metadataRefreshType: MetadataRefreshType.BOOKS,
            },
          },
        },
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: AppSettingsService, useValue: {appSettings}},
      ]
    });
  });

  it('reads dialog data on construction', () => {
    const component = TestBed.runInInjectionContext(() => new MultiBookMetadataFetchComponent());
    component.ngOnInit();

    expect(component.bookIds).toEqual([3, 5]);
    expect(component.metadataRefreshType).toBe(MetadataRefreshType.BOOKS);
  });

  it('gives precedence to dialogData Input over dynamicDialogConfig.data', () => {
    const component = TestBed.runInInjectionContext(() => new MultiBookMetadataFetchComponent());
    component.dialogData = {
      bookIds: [10],
      metadataRefreshType: MetadataRefreshType.BOOKS,
    };
    component.ngOnInit();

    expect(component.bookIds).toEqual([10]);
  });

  it('adopts the default metadata refresh options when app settings become available', () => {
    const component = TestBed.runInInjectionContext(() => new MultiBookMetadataFetchComponent());
    component.ngOnInit();

    expect(component.currentMetadataOptions).toBeUndefined();

    appSettings.set({
      defaultMetadataRefreshOptions: {
        libraryId: 7,
        refreshCovers: true,
        mergeCategories: false,
        reviewBeforeApply: true,
      },
    } as AppSettings);
    TestBed.flushEffects();

    expect(component.currentMetadataOptions).toEqual({
      libraryId: 7,
      refreshCovers: true,
      mergeCategories: false,
      reviewBeforeApply: true,
    });
  });
});
