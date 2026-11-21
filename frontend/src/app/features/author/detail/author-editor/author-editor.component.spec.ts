import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {DialogService} from '@openng/optimus-ui/dynamicdialog';
import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';

import {createQueryClientHarness, flushQueryAsync} from '../../../../core/testing/query-testing';
import {AuthorCommandService} from '../../data/author-command.service';
import {type UpdateAuthorVariables, type UploadAuthorPhotoVariables} from '../../data/author-command.models';
import {type AuthorDetail} from '../../data/author-query.models';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {AuthorEditorComponent} from './author-editor.component';

describe('AuthorEditorComponent', () => {
  let updateAuthor: Mock<(variables: UpdateAuthorVariables) => Promise<AuthorDetail>>;
  let uploadPhoto: Mock<(variables: UploadAuthorPhotoVariables) => Promise<void>>;
  let messageService: Pick<MessageService, 'add'>;

  const baseAuthor: AuthorDetail = {
    id: 9,
    name: 'Ada Lovelace',
    description: 'Analytical engine pioneer',
    asin: 'B00ADA',
    nameLocked: false,
    descriptionLocked: false,
    asinLocked: false,
    photoLocked: false,
  };

  const createComponent = (author: Partial<AuthorDetail> = {}) => {
    const fixture = TestBed.createComponent(AuthorEditorComponent);
    fixture.componentRef.setInput('authorId', 9);
    fixture.componentRef.setInput('author', {...baseAuthor, ...author});
    fixture.componentRef.setInput('photoTimestamp', 1);
    return {fixture, component: fixture.componentInstance};
  };

  beforeEach(() => {
    updateAuthor = vi.fn<(variables: UpdateAuthorVariables) => Promise<AuthorDetail>>();
    uploadPhoto = vi.fn<(variables: UploadAuthorPhotoVariables) => Promise<void>>().mockResolvedValue(undefined);
    messageService = {add: vi.fn()};
    const harness = createQueryClientHarness();

    TestBed.configureTestingModule({
      providers: [
        ...harness.providers,
        {
          provide: UrlHelperService,
          useValue: {getAuthorPhotoUrl: (authorId: number) => `/api/authors/${authorId}/photo`},
        },
        {
          provide: AuthorCommandService,
          useValue: {
            update: () => ({mutationFn: updateAuthor}),
            uploadPhoto: () => ({mutationFn: uploadPhoto}),
          },
        },
        {provide: MessageService, useValue: messageService},
        {provide: DialogService, useValue: {open: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: (key: string) => key}},
      ],
    });
    TestBed.overrideComponent(AuthorEditorComponent, {set: {template: '', imports: [], providers: []}});
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('initializes the form and disables controls whose lock flags start enabled', () => {
    const {component} = createComponent({nameLocked: true, asinLocked: true});

    component.ngOnInit();

    expect(component.form.get('name')?.value).toBe('Ada Lovelace');
    expect(component.form.get('name')?.disabled).toBe(true);
    expect(component.form.get('description')?.disabled).toBe(false);
    expect(component.form.get('asin')?.disabled).toBe(true);
  });

  it('saves the trimmed form values, tells the page, and reports success', async () => {
    updateAuthor.mockResolvedValue(baseAuthor);
    const {component} = createComponent();
    const updated = vi.fn();
    component.authorUpdated.subscribe(updated);
    component.ngOnInit();
    component.form.patchValue({name: '   ', description: '  Updated  ', asin: '  B00NEW  ', photoLocked: true});

    component.onSave();
    await flushQueryAsync();

    expect(updateAuthor.mock.calls[0][0]).toEqual({
      authorId: 9,
      request: {
        name: undefined,
        description: 'Updated',
        asin: 'B00NEW',
        nameLocked: false,
        descriptionLocked: false,
        asinLocked: false,
        photoLocked: true,
      },
    });
    expect(updated).toHaveBeenCalled();
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
  });

  it('shows the photo again when the page refreshes its photo timestamp', () => {
    const {fixture, component} = createComponent();
    component.onPhotoError();
    expect(component.hasPhoto()).toBe(false);

    fixture.componentRef.setInput('photoTimestamp', 2);

    expect(component.hasPhoto()).toBe(true);
  });

  it('uploads the chosen photo through the upload command and tells the page', async () => {
    const {component} = createComponent();
    const updated = vi.fn();
    component.authorUpdated.subscribe(updated);
    const file = new File(['photo'], 'photo.jpg', {type: 'image/jpeg'});

    component.onUpload({files: [file]});
    await flushQueryAsync();

    expect(uploadPhoto.mock.calls[0][0]).toEqual({authorId: 9, file});
    expect(updated).toHaveBeenCalled();
  });
});
