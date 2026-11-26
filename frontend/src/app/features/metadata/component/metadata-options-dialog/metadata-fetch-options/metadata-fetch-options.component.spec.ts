import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {Component, input, output, provideZonelessChangeDetection, SimpleChange} from '@angular/core';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';

import {AppSettings} from '../../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {MetadataRefreshOptions} from '../../../model/request/metadata-refresh-options.model';
import {TaskHelperService} from '../../../../settings/task-management/task-helper.service';
import {MetadataRefreshType} from '../../../model/request/metadata-refresh-type.enum';
import {MetadataFetchOptionsComponent} from './metadata-fetch-options.component';
import {MetadataAdvancedFetchOptionsComponent} from '../metadata-advanced-fetch-options/metadata-advanced-fetch-options.component';
import {getTranslocoModule} from '../../../../../core/testing/transloco-testing';

@Component({selector: 'app-metadata-advanced-fetch-options', standalone: true, template: ''})
class AdvancedFetchOptionsStub {
  currentMetadataOptions = input<MetadataRefreshOptions>();
  submitButtonLabel = input<string>();
  metadataOptionsSubmitted = output<MetadataRefreshOptions>();
}

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
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
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

  it('gives precedence to dialogData Input over dynamicDialogConfig.data', () => {
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
        {provide: AppSettingsService, useValue: {appSettings: () => null}},
      ]
    });

    const component = TestBed.runInInjectionContext(() => new MetadataFetchOptionsComponent());
    component.dialogData = {
      libraryId: 9,
      bookIds: [10],
      metadataRefreshType: MetadataRefreshType.BOOKS,
    };
    component.ngOnInit();

    expect(component.libraryId).toBe(9);
    expect(component.bookIds).toEqual([10]);
    expect(component.metadataRefreshType).toBe(MetadataRefreshType.BOOKS);
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

  it('ignores responses for outdated thresholds and library contexts', () => {
    TestBed.configureTestingModule({
      providers: [
        {provide: DynamicDialogConfig, useValue: {data: {libraryId: 7, metadataRefreshType: MetadataRefreshType.LIBRARY}}},
        {provide: DynamicDialogRef, useValue: {close}},
        {provide: TaskHelperService, useValue: {refreshMetadataTask}},
        {provide: AppSettingsService, useValue: {appSettings: () => null}},
      ]
    });
    const component = TestBed.runInInjectionContext(() => new MetadataFetchOptionsComponent());
    const http = TestBed.inject(HttpTestingController);
    component.ngOnInit();
    component.loadPreview();
    const oldRequest = http.expectOne(req => req.url.endsWith('/refresh-preview/7') && req.params.get('threshold') === '90');
    component.onPreviewThresholdChange(80);
    component.loadPreview();
    const currentRequest = http.expectOne(req => req.url.endsWith('/refresh-preview/7') && req.params.get('threshold') === '80');
    currentRequest.flush({total: 4, belowThreshold: 1, atOrAboveThreshold: 2, unknown: 1});
    oldRequest.flush({total: 100, belowThreshold: 100, atOrAboveThreshold: 0, unknown: 0});
    expect(component.preview()).toEqual({total: 4, belowThreshold: 1, atOrAboveThreshold: 2, unknown: 1});

    component.loadPreview();
    const previousLibraryRequest = http.expectOne(req => req.url.endsWith('/refresh-preview/7'));
    component.ngOnChanges({dialogData: new SimpleChange(null, {libraryId: 8, metadataRefreshType: MetadataRefreshType.LIBRARY}, false)});
    previousLibraryRequest.flush({total: 4, belowThreshold: 1, atOrAboveThreshold: 2, unknown: 1});
    expect(component.preview()).toBeNull();
    http.verify();
  });

  it('only submits an enabled valid threshold for library refreshes', () => {
    TestBed.configureTestingModule({
      providers: [
        {provide: DynamicDialogConfig, useValue: {data: {libraryId: 7, metadataRefreshType: MetadataRefreshType.LIBRARY}}},
        {provide: DynamicDialogRef, useValue: {close}},
        {provide: TaskHelperService, useValue: {refreshMetadataTask}},
        {provide: AppSettingsService, useValue: {appSettings: () => null}},
      ]
    });
    const component = TestBed.runInInjectionContext(() => new MetadataFetchOptionsComponent());
    component.ngOnInit();
    component.onMetadataSubmit(defaultMetadataRefreshOptions);
    expect(refreshMetadataTask).toHaveBeenCalledWith({
      refreshType: MetadataRefreshType.LIBRARY,
      refreshOptions: defaultMetadataRefreshOptions,
      bookIds: [],
      libraryId: 7,
    });
    refreshMetadataTask.mockClear();
    close.mockClear();
    component.applyThreshold.set(true);
    component.onPreviewThresholdChange(101);
    component.onMetadataSubmit(defaultMetadataRefreshOptions);
    expect(refreshMetadataTask).not.toHaveBeenCalled();
    expect(close).not.toHaveBeenCalled();

    component.onPreviewThresholdChange(80);
    component.onMetadataSubmit(defaultMetadataRefreshOptions);
    expect(refreshMetadataTask).toHaveBeenCalledWith(expect.objectContaining({scoreThreshold: 80}));
    expect(close).toHaveBeenCalledOnce();
  });

  it('submits database-only explicitly and rejects manual review', () => {
    TestBed.configureTestingModule({
      providers: [
        {provide: DynamicDialogConfig, useValue: {data: {bookIds: [1]}}},
        {provide: DynamicDialogRef, useValue: {close}},
        {provide: TaskHelperService, useValue: {refreshMetadataTask}},
        {provide: AppSettingsService, useValue: {appSettings: () => null}},
      ]
    });
    const component = TestBed.runInInjectionContext(() => new MetadataFetchOptionsComponent());
    component.ngOnInit();
    expect(component.databaseOnly()).toBe(false);
    component.databaseOnly.set(true);
    component.onMetadataSubmit({...defaultMetadataRefreshOptions, reviewBeforeApply: true});
    expect(component.databaseOnlyReviewConflict()).toBe(true);
    expect(refreshMetadataTask).not.toHaveBeenCalled();
    expect(close).not.toHaveBeenCalled();

    component.onMetadataSubmit(defaultMetadataRefreshOptions);
    expect(component.databaseOnlyReviewConflict()).toBe(false);
    expect(refreshMetadataTask).toHaveBeenCalledWith(expect.objectContaining({databaseOnly: true}));
    expect(close).toHaveBeenCalledOnce();
  });

  it('reports preview errors and rejects invalid thresholds', () => {
    TestBed.configureTestingModule({
      providers: [
        {provide: DynamicDialogConfig, useValue: {data: {libraryId: 7, metadataRefreshType: MetadataRefreshType.LIBRARY}}},
        {provide: DynamicDialogRef, useValue: {close}},
        {provide: TaskHelperService, useValue: {refreshMetadataTask}},
        {provide: AppSettingsService, useValue: {appSettings: () => null}},
      ]
    });
    const component = TestBed.runInInjectionContext(() => new MetadataFetchOptionsComponent());
    const http = TestBed.inject(HttpTestingController);
    component.ngOnInit();
    component.onPreviewThresholdChange(101);
    component.loadPreview();
    http.expectNone(req => req.url.includes('/refresh-preview/'));
    component.onPreviewThresholdChange(90);
    component.loadPreview();
    http.expectOne(req => req.url.endsWith('/refresh-preview/7')).flush('error', {status: 500, statusText: 'Error'});
    expect(component.previewError()).toBe(true);
    expect(component.previewLoading()).toBe(false);
    expect(component.preview()).toBeNull();
    http.verify();
  });

  it('renders preview results and errors after HTTP responses without another interaction', async () => {
    TestBed.configureTestingModule({
      imports: [MetadataFetchOptionsComponent, getTranslocoModule()],
      providers: [
        provideZonelessChangeDetection(),
        {provide: DynamicDialogConfig, useValue: {data: {libraryId: 7, metadataRefreshType: MetadataRefreshType.LIBRARY}}},
        {provide: DynamicDialogRef, useValue: {close}},
        {provide: TaskHelperService, useValue: {refreshMetadataTask}},
        {provide: AppSettingsService, useValue: {appSettings: () => null}},
      ],
    }).overrideComponent(MetadataFetchOptionsComponent, {
      remove: {imports: [MetadataAdvancedFetchOptionsComponent]},
      add: {imports: [AdvancedFetchOptionsStub]},
    });

    const fixture = TestBed.createComponent(MetadataFetchOptionsComponent);
    const http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
    const databaseOnlyCheckbox: HTMLInputElement = fixture.nativeElement.querySelector('.database-only input[type="checkbox"]');
    expect(databaseOnlyCheckbox.checked).toBe(false);
    expect(databaseOnlyCheckbox.getAttribute('aria-describedby')).toBe('database-only-notice');
    databaseOnlyCheckbox.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.databaseOnly()).toBe(true);
    const checkbox: HTMLInputElement = fixture.nativeElement.querySelector('.score-preview input[type="checkbox"]');
    expect(checkbox.checked).toBe(false);
    checkbox.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.applyThreshold()).toBe(true);
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.score-preview button');
    button.click();
    await fixture.whenStable();
    http.expectOne(req => req.url.endsWith('/refresh-preview/7'))
      .flush({total: 4, belowThreshold: 1, atOrAboveThreshold: 2, unknown: 1});
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.score-preview ul')?.textContent).toContain('4');
    expect(button.disabled).toBe(false);

    button.click();
    await fixture.whenStable();
    http.expectOne(req => req.url.endsWith('/refresh-preview/7'))
      .flush('error', {status: 500, statusText: 'Error'});
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.score-preview [role="alert"]')).not.toBeNull();
    expect(button.disabled).toBe(false);
    http.verify();
  });
});
