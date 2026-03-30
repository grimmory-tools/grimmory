import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {DomSanitizer} from '@angular/platform-browser';
import {firstValueFrom} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {SecurePipe} from './secure-pipe';

describe('SecurePipe', () => {
  let pipe: SecurePipe;
  let httpTestingController: HttpTestingController;
  let sanitizer: DomSanitizer;

  beforeEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        SecurePipe,
      ]
    });

    pipe = TestBed.inject(SecurePipe);
    httpTestingController = TestBed.inject(HttpTestingController);
    sanitizer = TestBed.inject(DomSanitizer);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('fetches the blob and returns a sanitized object URL', async () => {
    const objectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:cover');
    const sanitizerSpy = vi.spyOn(sanitizer, 'bypassSecurityTrustUrl').mockReturnValue('safe:cover' as never);

    const resultPromise = firstValueFrom(pipe.transform('/covers/1'));
    const request = httpTestingController.expectOne('/covers/1');
    request.flush(new Blob(['cover']));

    await expect(resultPromise).resolves.toBe('safe:cover');
    expect(objectUrlSpy).toHaveBeenCalledOnce();
    expect(sanitizerSpy).toHaveBeenCalledWith('blob:cover');
  });
});
