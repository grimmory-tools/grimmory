import {SimpleChange} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';
import {Subject, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import type {AuthorDetails} from '../../model/author.model';
import {AuthorService} from '../../service/author.service';
import {AuthorPhotoSearchComponent} from '../author-photo-search/author-photo-search.component';
import {AuthorEditorComponent} from './author-editor.component';

describe('AuthorEditorComponent', () => {
  let updateAuthor: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;
  let dialogClose$: Subject<boolean>;
  let messageService: Pick<MessageService, 'add'>;
  let translate: ReturnType<typeof vi.fn>;

  const baseAuthor: AuthorDetails = {
    id: 9,
    name: 'Ada Lovelace',
    description: 'Analytical engine pioneer',
    asin: 'B00ADA',
    nameLocked: false,
    descriptionLocked: false,
    asinLocked: false,
    photoLocked: false,
  };

  const createComponent = (overrides?: {authorId?: number; author?: Partial<AuthorDetails>}) => {
    const component = TestBed.runInInjectionContext(() => new AuthorEditorComponent());
    component.authorId = overrides?.authorId ?? 9;
    component.author = {
      ...baseAuthor,
      ...overrides?.author,
    };
    return component;
  };

  beforeEach(() => {
    updateAuthor = vi.fn();
    dialogClose$ = new Subject<boolean>();
    dialogOpen = vi.fn(() => ({onClose: dialogClose$}));
    messageService = {
      add: vi.fn(),
    };
    translate = vi.fn((key: string) => key);

    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthorService,
          useValue: {
            updateAuthor,
            getAuthorPhotoUrl: vi.fn((authorId: number) => `/api/authors/${authorId}/photo`),
            getUploadAuthorPhotoUrl: vi.fn((authorId: number) => `/api/authors/${authorId}/photo/upload`),
          },
        },
        {
          provide: MessageService,
          useValue: messageService,
        },
        {
          provide: DialogService,
          useValue: {
            open: dialogOpen,
          },
        },
        {
          provide: TranslocoService,
          useValue: {
            translate,
          },
        },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('initializes the form and disables controls whose lock flags start enabled', () => {
    const component = createComponent({
      author: {
        nameLocked: true,
        asinLocked: true,
      },
    });

    component.ngOnInit();

    expect(component.form.get('name')?.value).toBe('Ada Lovelace');
    expect(component.form.get('description')?.value).toBe('Analytical engine pioneer');
    expect(component.form.get('asin')?.value).toBe('B00ADA');
    expect(component.form.get('name')?.disabled).toBe(true);
    expect(component.form.get('description')?.disabled).toBe(false);
    expect(component.form.get('asin')?.disabled).toBe(true);
    expect(component.form.get('photoLocked')?.value).toBe(false);
  });

  it('restores photo state when a later author input change arrives', () => {
    const component = createComponent();
    const nowSpy = vi.spyOn(Date, 'now').mockReturnValue(777);

    component.hasPhoto = false;
    component.photoTimestamp = 10;
    component.ngOnChanges({
      author: new SimpleChange(baseAuthor, {...baseAuthor, name: 'Updated Ada'}, false),
    });

    expect(component.hasPhoto).toBe(true);
    expect(component.photoTimestamp).toBe(777);
    expect(nowSpy).toHaveBeenCalled();
  });

  it('shapes the save request from raw form values, emits the updated author, and reports success', () => {
    const updatedAuthor: AuthorDetails = {
      ...baseAuthor,
      description: 'Updated description',
      asin: 'B00NEW',
      nameLocked: true,
      asinLocked: true,
      photoLocked: true,
    };
    const save$ = new Subject<AuthorDetails>();
    updateAuthor.mockReturnValue(save$);

    const component = createComponent();
    const emitSpy = vi.spyOn(component.authorUpdated, 'emit');

    component.ngOnInit();
    component.form.patchValue({
      name: '   ',
      description: '  Updated description  ',
      asin: '  B00NEW  ',
      nameLocked: true,
      descriptionLocked: false,
      asinLocked: true,
      photoLocked: true,
    });

    component.onSave();

    expect(component.isSaving()).toBe(true);
    expect(updateAuthor).toHaveBeenCalledWith(9, {
      name: undefined,
      description: 'Updated description',
      asin: 'B00NEW',
      nameLocked: true,
      descriptionLocked: false,
      asinLocked: true,
      photoLocked: true,
    });

    save$.next(updatedAuthor);
    save$.complete();

    expect(component.isSaving()).toBe(false);
    expect(emitSpy).toHaveBeenCalledWith(updatedAuthor);
    expect(translate).toHaveBeenCalledWith('authorBrowser.editor.toast.successSummary');
    expect(translate).toHaveBeenCalledWith('authorBrowser.editor.toast.successDetail');
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'authorBrowser.editor.toast.successSummary',
      detail: 'authorBrowser.editor.toast.successDetail',
    });
  });

  it('clears the saving flag and reports an error toast when saveMetadata fails', () => {
    updateAuthor.mockReturnValue(throwError(() => new Error('boom')));

    const component = createComponent();
    const emitSpy = vi.spyOn(component.authorUpdated, 'emit');
    component.ngOnInit();

    component.onSave();

    expect(component.isSaving()).toBe(false);
    expect(emitSpy).not.toHaveBeenCalled();
    expect(translate).toHaveBeenCalledWith('authorBrowser.editor.toast.errorSummary');
    expect(translate).toHaveBeenCalledWith('authorBrowser.editor.toast.errorDetail');
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'authorBrowser.editor.toast.errorSummary',
      detail: 'authorBrowser.editor.toast.errorDetail',
    });
  });

  it('opens the photo search dialog and refreshes photo state when it closes with success', () => {
    const component = createComponent({authorId: 42});
    const emitSpy = vi.spyOn(component.authorUpdated, 'emit');
    const nowSpy = vi.spyOn(Date, 'now').mockReturnValue(888);

    component.hasPhoto = false;
    component.photoTimestamp = 12;

    component.openPhotoSearch();

    expect(dialogOpen).toHaveBeenCalledWith(AuthorPhotoSearchComponent, {
      data: {authorId: 42, authorName: 'Ada Lovelace'},
      header: 'authorBrowser.editor.searchPhotoTitle',
      width: '70vw',
      height: '80vh',
      modal: true,
      closable: true,
      dismissableMask: true,
      contentStyle: {'overflow': 'hidden', 'padding': '0', 'display': 'flex', 'flex-direction': 'column'},
    });

    dialogClose$.next(true);

    expect(component.hasPhoto).toBe(true);
    expect(component.photoTimestamp).toBe(888);
    expect(emitSpy).toHaveBeenCalledWith(component.author);
    expect(nowSpy).toHaveBeenCalled();
  });

  it('marks upload success, refreshes the photo timestamp, and shows a success toast', () => {
    const component = createComponent();
    const emitSpy = vi.spyOn(component.authorUpdated, 'emit');
    const nowSpy = vi.spyOn(Date, 'now').mockReturnValue(999);

    component.isUploading.set(true);
    component.hasPhoto = false;

    component.onUpload();

    expect(component.isUploading()).toBe(false);
    expect(component.hasPhoto).toBe(true);
    expect(component.photoTimestamp).toBe(999);
    expect(emitSpy).toHaveBeenCalledWith(component.author);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'authorBrowser.editor.toast.photoUploadedSummary',
      detail: 'authorBrowser.editor.toast.photoUploadedDetail',
    });
    expect(nowSpy).toHaveBeenCalled();
  });

  it('clears upload progress and shows an error toast when upload fails', () => {
    const component = createComponent();
    component.isUploading.set(true);

    component.onUploadError();

    expect(component.isUploading()).toBe(false);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'authorBrowser.editor.toast.errorSummary',
      detail: 'authorBrowser.editor.toast.photoUploadErrorDetail',
    });
  });
});
