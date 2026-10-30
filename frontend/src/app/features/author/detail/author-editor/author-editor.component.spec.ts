import {SimpleChange} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {DialogService} from '@openng/optimus-ui/dynamicdialog';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';

import {createQueryClientHarness, flushQueryAsync} from '../../../../core/testing/query-testing';
import {AuthorCommandService, type UpdateAuthorVariables} from '../../data/author-command.service';
import {authorQueryKeys} from '../../data/author-query-keys';
import {type AuthorDetail} from '../../data/author-response.models';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {AuthorEditorComponent} from './author-editor.component';

describe('AuthorEditorComponent', () => {
  let updateAuthor: Mock<(variables: UpdateAuthorVariables) => Promise<AuthorDetail>>;
  let messageService: Pick<MessageService, 'add'>;
  let queryClient: QueryClient;

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
    const component = TestBed.runInInjectionContext(() => new AuthorEditorComponent());
    component.authorId = 9;
    component.author = {...baseAuthor, ...author};
    component.photoTimestamp = 1;
    return component;
  };

  beforeEach(() => {
    updateAuthor = vi.fn<(variables: UpdateAuthorVariables) => Promise<AuthorDetail>>();
    messageService = {add: vi.fn()};
    const harness = createQueryClientHarness();
    queryClient = harness.queryClient;

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
            photoUploadUrl: (authorId: number) => `/api/authors/${authorId}/photo/upload`,
          },
        },
        {provide: MessageService, useValue: messageService},
        {provide: DialogService, useValue: {open: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: (key: string) => key}},
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('initializes the form and disables controls whose lock flags start enabled', () => {
    const component = createComponent({nameLocked: true, asinLocked: true});

    component.ngOnInit();

    expect(component.form.get('name')?.value).toBe('Ada Lovelace');
    expect(component.form.get('name')?.disabled).toBe(true);
    expect(component.form.get('description')?.disabled).toBe(false);
    expect(component.form.get('asin')?.disabled).toBe(true);
  });

  it('saves the trimmed form values, tells the page, and reports success', async () => {
    updateAuthor.mockResolvedValue(baseAuthor);
    const component = createComponent();
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
    const component = createComponent();
    component.hasPhoto = false;

    component.ngOnChanges({photoTimestamp: new SimpleChange(1, 2, false)});

    expect(component.hasPhoto).toBe(true);
  });

  it('refreshes the author lists after a direct photo upload', () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    const component = createComponent();
    const updated = vi.fn();
    component.authorUpdated.subscribe(updated);

    component.onUpload();

    expect(invalidate).toHaveBeenCalledWith({queryKey: authorQueryKeys.collections()});
    expect(updated).toHaveBeenCalled();
  });
});
