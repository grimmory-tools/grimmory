import {ChangeDetectorRef, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {MessageService} from '@openng/optimus-ui/api';
import {DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {TranslocoService} from '@jsverse/transloco';
import {LibraryService} from '../../../features/book/service/library.service';
import {AppSettingsService} from '../../service/app-settings.service';
import {API_CONFIG} from '../../../core/config/api-config';
import {BookUploaderComponent} from './book-uploader.component';

describe('BookUploaderComponent LCP imports', () => {
  let component: BookUploaderComponent;
  let http: HttpTestingController;
  const dialog = {close: vi.fn()};
  const base = API_CONFIG.BASE_URL;

  beforeEach(() => {
    vi.useFakeTimers();
    dialog.close.mockClear();
    TestBed.configureTestingModule({providers: [
      provideHttpClient(), provideHttpClientTesting(),
      {provide: LibraryService, useValue: {libraries: signal([])}},
      {provide: AppSettingsService, useValue: {appSettings: signal({maxFileUploadSizeInMb: 100})}},
      {provide: MessageService, useValue: {add: vi.fn()}},
      {provide: DynamicDialogRef, useValue: dialog},
      {provide: TranslocoService, useValue: {translate: (key: string) => key}},
      {provide: ChangeDetectorRef, useValue: {detectChanges: vi.fn(), markForCheck: vi.fn()}}
    ]});
    component = TestBed.runInInjectionContext(() => new BookUploaderComponent());
    component.value = 'bookdrop';
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => { http.verify(); vi.useRealTimers(); });

  function addLicense(code?: string) {
    component.files = [{file: new File(['{}'], 'fixture.lcpl'), status: 'Pending', progress: 0, lcpPassphrase: code}];
  }

  it('requires a code when the user has no saved default', () => {
    http.expectOne(`${base}/api/v1/users/me/lcp-settings`).flush({hasDefaultPassphrase: false});
    addLicense();
    expect(component.isUploadDisabled()).toBe(true);
    component.files[0].lcpPassphrase = 'one-time-code';
    expect(component.isUploadDisabled()).toBe(false);
  });

  it('uses a saved default without sending a code or exposing it', () => {
    http.expectOne(`${base}/api/v1/users/me/lcp-settings`).flush({hasDefaultPassphrase: true});
    addLicense();
    component.uploadFiles({files: []});
    const request = http.expectOne(`${base}/api/v1/files/upload/bookdrop`);
    expect((request.request.body as FormData).has('lcpPassphrase')).toBe(false);
    request.flush(null);
    vi.runAllTimers();
    expect(dialog.close).toHaveBeenCalledWith('uploaded_to_bookdrop');
  });

  it('keeps failed conversions open and retries with an override', () => {
    http.expectOne(`${base}/api/v1/users/me/lcp-settings`).flush({hasDefaultPassphrase: true});
    addLicense();
    component.uploadFiles({files: []});
    http.expectOne(`${base}/api/v1/files/upload/bookdrop`).flush({message: 'Incorrect LCP code'}, {status: 422, statusText: 'Unprocessable Entity'});
    vi.runAllTimers();
    expect(dialog.close).not.toHaveBeenCalled();
    expect(component.files[0].status).toBe('Failed');
    component.files[0].lcpPassphrase = 'override-code';
    component.retryFile(component.files[0]);
    component.uploadFiles({files: []});
    const retry = http.expectOne(`${base}/api/v1/files/upload/bookdrop`);
    expect((retry.request.body as FormData).get('lcpPassphrase')).toBe('override-code');
    retry.flush(null);
    vi.runAllTimers();
    expect(component.files[0].lcpPassphrase).toBeUndefined();
    expect(dialog.close).toHaveBeenCalledWith('uploaded_to_bookdrop');
  });
});
