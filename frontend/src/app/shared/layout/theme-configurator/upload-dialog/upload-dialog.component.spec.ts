import {ComponentFixture, TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {TranslocoService} from '@jsverse/transloco';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {BackgroundUploadService} from '../background-upload.service';
import {UploadDialogComponent} from './upload-dialog.component';

describe('UploadDialogComponent', () => {
  let fixture: ComponentFixture<UploadDialogComponent>;
  let component: UploadDialogComponent;
  let dialogRef: {close: ReturnType<typeof vi.fn>};
  let backgroundUploadService: {
    uploadFile: ReturnType<typeof vi.fn>;
    uploadUrl: ReturnType<typeof vi.fn>;
  };
  let translocoService: TranslocoService;

  beforeEach(async () => {
    dialogRef = {
      close: vi.fn(),
    };
    backgroundUploadService = {
      uploadFile: vi.fn(() => of('https://example.com/uploaded.png')),
      uploadUrl: vi.fn(() => of('https://example.com/from-url.png')),
    };

    await TestBed.configureTestingModule({
      imports: [UploadDialogComponent, getTranslocoModule()],
      providers: [
        {provide: DynamicDialogRef, useValue: dialogRef},
        {provide: BackgroundUploadService, useValue: backgroundUploadService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UploadDialogComponent);
    component = fixture.componentInstance;
    translocoService = TestBed.inject(TranslocoService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('stores the selected file and clears the URL field', () => {
    const file = new File(['image'], 'cover.png', {type: 'image/png'});
    const event = {
      target: {
        files: [file],
      },
    } as unknown as Event;
    component.uploadImageUrl = 'https://example.com/old.png';

    component.onFileSelected(event);

    expect(component.uploadFile).toBe(file);
    expect(component.uploadImageUrl).toBe('');
  });

  it('requires either a file or a URL before uploading', () => {
    component.submit();

    expect(component.uploadError).toBe(
      translocoService.translate('layout.uploadDialog.errorNoInput')
    );
    expect(backgroundUploadService.uploadFile).not.toHaveBeenCalled();
    expect(backgroundUploadService.uploadUrl).not.toHaveBeenCalled();
  });

  it('uploads a selected file and closes the dialog with the returned URL', () => {
    const file = new File(['image'], 'cover.png', {type: 'image/png'});
    component.uploadFile = file;

    component.submit();

    expect(backgroundUploadService.uploadFile).toHaveBeenCalledWith(file);
    expect(dialogRef.close).toHaveBeenCalledWith({
      imageUrl: 'https://example.com/uploaded.png',
    });
  });

  it('uploads a trimmed URL and closes the dialog with the returned value', () => {
    component.uploadImageUrl = ' https://example.com/bg.png ';

    component.submit();

    expect(backgroundUploadService.uploadUrl).toHaveBeenCalledWith('https://example.com/bg.png');
    expect(dialogRef.close).toHaveBeenCalledWith({
      imageUrl: 'https://example.com/from-url.png',
    });
  });

  it('shows an error when the upload succeeds without returning an image URL', () => {
    backgroundUploadService.uploadUrl.mockReturnValueOnce(of(''));
    component.uploadImageUrl = 'https://example.com/bg.png';

    component.submit();

    expect(component.uploadError).toBe('Failed to upload image.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('shows a generic upload error when the upload request fails', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    backgroundUploadService.uploadFile.mockReturnValueOnce(
      throwError(() => new Error('boom'))
    );
    component.uploadFile = new File(['image'], 'cover.png', {type: 'image/png'});

    component.submit();

    expect(component.uploadError).toBe('Upload failed. Please try again.');
    expect(console.error).toHaveBeenCalled();
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('closes the dialog when cancel is clicked', () => {
    component.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
