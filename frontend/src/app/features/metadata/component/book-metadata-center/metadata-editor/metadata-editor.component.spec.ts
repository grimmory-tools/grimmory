import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {MessageService} from '@openng/optimus-ui/api';
import {of} from 'rxjs';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {Book} from '../../../../book/model/book.model';
import {BookService} from '../../../../book/service/book.service';
import {BookMetadataManageService} from '../../../../book/service/book-metadata-manage.service';
import {BookDialogHelperService} from '../../../../book/service/book-dialog-helper.service';
import {BookNavigationService} from '../../../../book/service/book-navigation.service';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {BookMetadataHostService} from '../../../../../shared/service/book-metadata-host.service';
import {MetadataProviderFieldsService} from '../../../../../shared/metadata';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {UserService} from '../../../../settings/user-management/user.service';
import {TaskHelperService} from '../../../../settings/task-management/task-helper.service';
import {MetadataSourceQueryService} from '../../../sources/metadata-source-query.service';
import {MetadataEditorComponent} from './metadata-editor.component';
import {Router} from '@angular/router';
import {TranslocoService} from '@jsverse/transloco';

describe('MetadataEditorComponent manual lock behavior', () => {
  afterEach(() => TestBed.resetTestingModule());
  it('keeps locked fields editable and submits with ALLOW_LOCKED_FIELD_EDITS', () => {
    const updateBookMetadata = vi.fn(() => of({}));
    const regenerateCover = vi.fn(() => of(void 0));
    const generateCustomCover = vi.fn(() => of(void 0));
    const regenerateAudiobookCover = vi.fn(() => of(void 0));
    const generateCustomAudiobookCover = vi.fn(() => of(void 0));
    const getUploadCoverUrl = vi.fn(() => 'ebook-cover-upload');
    const getUploadAudiobookCoverUrl = vi.fn(() => 'audiobook-cover-upload');
    const messageAdd = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        {provide: MessageService, useValue: {add: messageAdd}},
        {provide: BookService, useValue: {}},
        {provide: BookMetadataManageService, useValue: {
          updateBookMetadata,
          regenerateCover,
          generateCustomCover,
          regenerateAudiobookCover,
          generateCustomAudiobookCover,
          getUploadCoverUrl,
          getUploadAudiobookCoverUrl,
        }},

        {provide: TaskHelperService, useValue: {}},
        {provide: UrlHelperService, useValue: {}},
        {provide: BookDialogHelperService, useValue: {}},
        {provide: BookNavigationService, useValue: {}},
        {provide: BookMetadataHostService, useValue: {}},
        {provide: Router, useValue: {}},
        {provide: UserService, useValue: {}},
        {provide: AppSettingsService, useValue: {}},
        {provide: TranslocoService, useValue: {translate: (key: string) => key}},
        {provide: QueryClient, useValue: new QueryClient()},
        {provide: MetadataSourceQueryService, useValue: {}},
        {provide: MetadataProviderFieldsService, useValue: {fields: () => []}},
      ],
    });

    const component = TestBed.runInInjectionContext(() => new MetadataEditorComponent());
    component.book = {
      id: 21,
      metadata: {
        bookId: 21,
        title: 'Locked title',
        titleLocked: true,
        narrator: 'Locked narrator',
        narratorLocked: true,
        comicMetadata: {issueNumber: '1', issueNumberLocked: true},
      },
    } as unknown as Book;

    expect(component.metadataForm.get('title')?.enabled).toBe(true);
    expect(component.metadataForm.get('narrator')?.enabled).toBe(true);
    expect(component.metadataForm.get('comicIssueNumber')?.enabled).toBe(true);

    component.saveMetadata().subscribe();

    expect(updateBookMetadata).toHaveBeenCalledWith(
      21,
      expect.any(Object),
      false,
      'REPLACE_ALL',
      'ALLOW_LOCKED_FIELD_EDITS',
    );

    expect(component.getUploadCoverUrl()).toBe('ebook-cover-upload');
    expect(component.getUploadAudiobookCoverUrl()).toBe('audiobook-cover-upload');
    component.regenerateCover(21);
    component.generateCustomCover(21);
    component.regenerateAudiobookCover(21);
    component.generateCustomAudiobookCover(21);

    expect(getUploadCoverUrl).toHaveBeenCalledWith(21, 'ALLOW_LOCKED_FIELD_EDITS');
    expect(getUploadAudiobookCoverUrl).toHaveBeenCalledWith(21, 'ALLOW_LOCKED_FIELD_EDITS');
    expect(regenerateCover).toHaveBeenCalledWith(21, 'ALLOW_LOCKED_FIELD_EDITS');
    expect(generateCustomCover).toHaveBeenCalledWith(21, 'ALLOW_LOCKED_FIELD_EDITS');
    expect(regenerateAudiobookCover).toHaveBeenCalledWith(21, 'ALLOW_LOCKED_FIELD_EDITS');
    expect(generateCustomAudiobookCover).toHaveBeenCalledWith(21, 'ALLOW_LOCKED_FIELD_EDITS');
  });
});
