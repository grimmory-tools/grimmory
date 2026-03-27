import {Component} from '@angular/core';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {SecureSrcDirective} from './secure-src.directive';

@Component({
  standalone: true,
  imports: [SecureSrcDirective],
  template: `<img [appSecureSrc]="src" data-fallback-src="/fallback.png" alt="test cover" />`
})
class TestHostComponent {
  src = '';
}

@Component({
  standalone: true,
  imports: [SecureSrcDirective],
  template: `<img [appSecureSrc]="src" alt="test cover" />`
})
class DefaultFallbackHostComponent {
  src = '';
}

describe('SecureSrcDirective', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TestHostComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ]
    });

    fixture = TestBed.createComponent(TestHostComponent);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  function image(): HTMLImageElement {
    return fixture.debugElement.query(By.css('img')).nativeElement as HTMLImageElement;
  }

  it('loads a secure blob URL into the host image', () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:secure-cover');

    fixture.componentInstance.src = '/covers/1';
    fixture.detectChanges();

    const request = httpTestingController.expectOne('/covers/1');
    request.flush(new Blob(['cover']));

    expect(image().src).toContain('blob:secure-cover');
  });

  it('skips loading when the source is empty', () => {
    fixture.detectChanges();

    httpTestingController.expectNone(() => true);
    expect(image().getAttribute('src')).toBeNull();
  });

  it('uses the fallback image and suppresses CORS-style console noise for status 0 and 403', () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    fixture.componentInstance.src = '/covers/2';
    fixture.detectChanges();

    const firstRequest = httpTestingController.expectOne('/covers/2');
    firstRequest.error(new ProgressEvent('error'), {status: 0, statusText: 'Unknown Error'});

    expect(image().src).toContain('/fallback.png');
    expect(errorSpy).not.toHaveBeenCalled();

    fixture.componentInstance.src = '/covers/3';
    fixture.detectChanges();

    const secondRequest = httpTestingController.expectOne('/covers/3');
    secondRequest.flush(new Blob(['forbidden']), {status: 403, statusText: 'Forbidden'});

    expect(image().src).toContain('/fallback.png');
    expect(errorSpy).not.toHaveBeenCalled();
  });

  it('logs unexpected failures and revokes prior object URLs during cleanup', () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(URL, 'createObjectURL').mockReturnValueOnce('blob:first').mockReturnValueOnce('blob:second');

    fixture.componentInstance.src = '/covers/4';
    fixture.detectChanges();
    httpTestingController.expectOne('/covers/4').flush(new Blob(['first']));

    fixture.componentInstance.src = '/covers/5';
    fixture.detectChanges();

    expect(revokeSpy).toHaveBeenCalledWith('blob:first');

    httpTestingController.expectOne('/covers/5').flush(new Blob(['boom']), {status: 500, statusText: 'Server Error'});

    expect(image().src).toContain('/fallback.png');
    expect(errorSpy).toHaveBeenCalledOnce();

    fixture.destroy();
  });

  it('revokes the active object URL when the directive is destroyed after a successful load', () => {
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:destroyed');

    fixture.componentInstance.src = '/covers/7';
    fixture.detectChanges();

    const request = httpTestingController.expectOne('/covers/7');
    request.flush(new Blob(['cover']));

    fixture.destroy();

    expect(revokeSpy).toHaveBeenCalledWith('blob:destroyed');
  });

  it('uses the default fallback image when no fallback attribute is provided', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [DefaultFallbackHostComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ]
    });

    const defaultFixture = TestBed.createComponent(DefaultFallbackHostComponent);
    const defaultHttpTestingController = TestBed.inject(HttpTestingController);
    defaultFixture.componentInstance.src = '/covers/6';
    defaultFixture.detectChanges();

    const request = defaultHttpTestingController.expectOne('/covers/6');
    request.error(new ProgressEvent('error'), {status: 500, statusText: 'Server Error'});

    expect(defaultFixture.debugElement.query(By.css('img')).nativeElement.src).toContain('assets/images/missing-cover.jpg');
    defaultHttpTestingController.verify();
  });
});
