import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';

import {AppSettings} from '../../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {MetadataRefreshOptions} from '../../../model/request/metadata-refresh-options.model';
import {TaskHelperService} from '../../../../settings/task-management/task-helper.service';
import {MetadataRefreshType} from '../../../model/request/metadata-refresh-type.enum';
import {MetadataFetchOptionsComponent} from './metadata-fetch-options.component';

describe('MetadataFetchOptionsComponent', () => {
  const close = vi.fn();
  const refreshMetadataTask = vi.fn(() => of(void 0));
  const defaultMetadataRefreshOptions: MetadataRefreshOptions = {
    libraryId: null,
    refreshCovers: true,
    mergeCategories: false,
    reviewBeforeApply: false,
  };

  beforeEach(() => {
    close.mockClear();
    refreshMetadataTask.mockClear();
  });

  it('reads dialog data and current default metadata options on construction', () => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: DynamicDialogConfig,
          useValue: {
            data: {
              libraryId: 7,
              bookIds: [1, 2],
              metadataRefreshType: MetadataRefreshType.LIBRARY,
            },
          },
        },
        {provide: DynamicDialogRef, useValue: {close}},
        {provide: TaskHelperService, useValue: {refreshMetadataTask}},
        {
          provide: AppSettingsService,
          useValue: {
            appSettings: () => ({
              defaultMetadataRefreshOptions,
            } as AppSettings),
          },
        },
      ]
    });

    const component = TestBed.runInInjectionContext(() => new MetadataFetchOptionsComponent());
    component.ngOnInit();

    expect(component.libraryId).toBe(7);
    expect(component.bookIds).toEqual([1, 2]);
    expect(component.metadataRefreshType).toBe(MetadataRefreshType.LIBRARY);
    expect(component.currentMetadataOptions).toEqual(defaultMetadataRefreshOptions);
  });

  it('submits a metadata refresh task and closes the dialog', () => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: DynamicDialogConfig,
          useValue: {
            data: {
              libraryId: 7,
              bookIds: [1, 2],
              metadataRefreshType: MetadataRefreshType.BOOKS,
            },
          },
        },
        {provide: DynamicDialogRef, useValue: {close}},
        {provide: TaskHelperService, useValue: {refreshMetadataTask}},
        {provide: AppSettingsService, useValue: {appSettings: () => null}},
      ]
    });

    const component = TestBed.runInInjectionContext(() => new MetadataFetchOptionsComponent());
    component.ngOnInit();
    const options: MetadataRefreshOptions = {
      libraryId: 7,
      refreshCovers: false,
      mergeCategories: true,
      reviewBeforeApply: true,
    };

    component.onMetadataSubmit(options);

    expect(refreshMetadataTask).toHaveBeenCalledWith({
      refreshType: MetadataRefreshType.BOOKS,
      refreshOptions: options,
      bookIds: [1, 2],
      libraryId: 7,
    });
    expect(close).toHaveBeenCalledOnce();
  });
});
