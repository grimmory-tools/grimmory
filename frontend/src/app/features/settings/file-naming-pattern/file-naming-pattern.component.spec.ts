import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';

import {MessageService} from 'primeng/api';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {LibraryService} from '../../book/service/library.service';
import {FileNamingPatternComponent} from './file-naming-pattern.component';

describe('FileNamingPatternComponent', () => {
  let fixture: ComponentFixture<FileNamingPatternComponent>;
  let component: FileNamingPatternComponent;
  let saveSettings: ReturnType<typeof vi.fn>;
  let updateLibraryFileNamingPattern: ReturnType<typeof vi.fn>;
  let messageService: {add: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    saveSettings = vi.fn(() => of(void 0));
    updateLibraryFileNamingPattern = vi.fn(() => of(void 0));
    messageService = {add: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [FileNamingPatternComponent, getTranslocoModule()],
      providers: [
        {provide: AppSettingsService, useValue: {appSettings: () => ({uploadPattern: '{authors}/{title}'}), saveSettings}},
        {
          provide: LibraryService,
          useValue: {
            libraries: () => [
              {id: 1, name: 'Main', fileNamingPattern: '{series}/{title}'},
              {id: 2, name: 'Side', fileNamingPattern: ''},
            ],
            updateLibraryFileNamingPattern,
          },
        },
        {provide: MessageService, useValue: messageService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FileNamingPatternComponent);
    component = fixture.componentInstance;
    component.ngOnInit();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('hydrates the default pattern and generates previews with extensions', () => {
    expect(component.defaultPattern).toBe('{authors}/{title}');
    expect(component.generateDefaultPreview()).toBe('/Patrick Rothfuss/The Name of the Wind.pdf');
  });

  it('validates patterns and exposes invalid-character errors', () => {
    component.onDefaultPatternChange('bad%^pattern');

    expect(component.validatePattern('bad%^pattern')).toBe(false);
    expect(component.defaultErrorMessage).toBeTruthy();
  });

  it('persists the default upload pattern when validation passes', () => {
    component.defaultPattern = '{title}/{series}';

    component.savePatterns();

    expect(saveSettings).toHaveBeenCalledWith([
      {key: 'UPLOAD_FILE_PATTERN', newValue: '{title}/{series}'},
    ]);
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
  });

  it('saves library overrides and reports partial failures', () => {
    updateLibraryFileNamingPattern
      .mockReturnValueOnce(of(void 0))
      .mockReturnValueOnce(throwError(() => new Error('boom')));

    component.saveLibraryPatterns();

    expect(updateLibraryFileNamingPattern).toHaveBeenCalledTimes(2);
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
  });
});
