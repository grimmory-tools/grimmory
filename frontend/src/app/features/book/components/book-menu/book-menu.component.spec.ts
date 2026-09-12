import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {ConfirmationService} from '@openng/optimus-ui/api';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {signal, type WritableSignal} from '@angular/core';

import {createQueryClientHarness} from '../../../../core/testing/query-testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {UserService} from '../../../settings/user-management/user.service';
import {MetadataRefreshSubmissionService} from '../../../metadata/data/metadata-refresh-submission.service';
import {BookBackgroundSubmissionService} from '../../data/book-background-submission.service';
import {BookCommandService} from '../../data/book-command.service';
import {BookShelfCommandService} from '../../data/book-shelf-command.service';
import {ShelfDefinitionQueryService} from '../../data/shelf-definition-query.service';
import {BookSummary} from '../../data/book-response.models';
import {BookDialogHelperService} from '../book-browser/book-dialog-helper.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {FileDownloadService} from '../../../../shared/service/file-download.service';
import {BookNavigationService} from '../../service/book-navigation.service';
import {BookMenuComponent} from './book-menu.component';

type Permissions = Record<string, boolean>;

const NO_PERMISSIONS: Permissions = {};
const ALL_PERMISSIONS: Permissions = {
  canDownload: true,
  canEmailBook: true,
  canEditMetadata: true,
  canDeleteBook: true,
  canBulkResetGrimmoryReadProgress: true,
  canBulkResetKoReaderReadProgress: true,
  canMoveOrganizeFiles: true,
};

function makeBook(overrides: Partial<BookSummary> = {}): BookSummary {
  return {
    id: 1,
    libraryId: 1,
    libraryName: 'Library',
    metadata: {bookId: 1, title: 'The Warden', allMetadataLocked: false},
    primaryFile: {id: 10, bookId: 1, book: true, folderBased: false, bookType: 'EPUB'},
    ...overrides,
  };
}

describe('BookMenuComponent row gating', () => {
  let fixture: ComponentFixture<BookMenuComponent>;
  let element: HTMLElement;
  let permissions: WritableSignal<Permissions>;
  let diskType: WritableSignal<string>;

  beforeEach(() => {
    permissions = signal(NO_PERMISSIONS);
    diskType = signal('LOCAL');
    const harness = createQueryClientHarness();

    TestBed.configureTestingModule({
      imports: [BookMenuComponent, getTranslocoModule()],
      providers: [
        ...harness.providers,
        provideRouter([{path: '**', children: []}]),
        {provide: UserService, useValue: {currentUser: () => ({id: 1, permissions: permissions()})}},
        {provide: ShelfDefinitionQueryService, useValue: {definitions: () => ({queryKey: ['shelves'], queryFn: () => []})}},
        {provide: MetadataRefreshSubmissionService, useValue: {refreshMetadata: () => ({mutationFn: () => Promise.resolve()})}},
        {provide: BookBackgroundSubmissionService, useValue: {
          changeCovers: () => ({mutationFn: () => Promise.resolve()}),
          quickSend: () => ({mutationFn: () => Promise.resolve()}),
        }},
        {provide: BookCommandService, useValue: {
          setReadStatus: () => ({mutationFn: () => Promise.resolve([])}),
          deleteBooks: () => ({mutationFn: () => Promise.resolve({removedBookIds: []})}),
          deleteAdditionalFile: () => ({mutationFn: () => Promise.resolve({bookId: 1, fileId: 10})}),
          setAllMetadataLocks: () => ({mutationFn: () => Promise.reject(new Error('failed'))}),
          resetProgress: () => ({mutationFn: () => Promise.resolve([])}),
        }},
        {provide: BookShelfCommandService, useValue: {
          updateMembership: () => ({
            mutationKey: ['test', 'shelf-membership'],
            mutationFn: () => Promise.resolve({
              confirmedBookIds: [1],
              updatedBookShelves: [{bookId: 1, shelves: []}],
            }),
          }),
        }},
        {provide: BookDialogHelperService, useValue: {}},
        {provide: AppSettingsService, useValue: {appSettings: () => ({diskType: diskType()})}},
        {provide: FileDownloadService, useValue: {downloadFile: () => undefined}},
        {provide: BookNavigationService, useValue: {readBook: () => undefined}},
        {provide: ConfirmationService, useValue: {confirm: () => undefined}},
      ],
    });

    fixture = TestBed.createComponent(BookMenuComponent);
    element = fixture.nativeElement as HTMLElement;
    fixture.componentRef.setInput('openInNewTab', true);
    showBook(makeBook());
  });

  function showBook(book: BookSummary | null): void {
    (fixture.componentInstance as unknown as {bookSnapshot: {set(value: BookSummary | null): void}})
      .bookSnapshot.set(book);
  }

  function rowLabels(): string[] {
    fixture.detectChanges();
    const root = element.querySelector('app-menu') as HTMLElement;
    return Array.from(root.querySelectorAll(':scope > app-menu-item, :scope > div > app-menu-item')).map(
      el => el.textContent?.trim() ?? '',
    );
  }

  function row(label: string): HTMLElement {
    fixture.detectChanges();
    const root = element.querySelector('app-menu') as HTMLElement;
    return Array.from(root.querySelectorAll('app-menu-item'))
      .find(el => el.textContent?.trim() === label) as HTMLElement;
  }

  function hasSubmenu(label: string): boolean {
    return row(label).getAttribute('aria-haspopup') === 'true';
  }

  function submenuLabels(label: string): string[] {
    fixture.detectChanges();
    const panel = element.querySelector(`app-menu[aria-label="${label}"]`) as HTMLElement;
    return Array.from(panel.querySelectorAll('app-menu-item')).map(el => el.textContent?.trim() ?? '');
  }

  it('gates each row on permissions', () => {
    expect(rowLabels()).toEqual(['Read Book', 'Open Details in New Tab', 'Add to Shelf', 'Mark as']);

    permissions.set(ALL_PERMISSIONS);
    expect(rowLabels()).toEqual([
      'Read Book',
      'Open Details in New Tab',
      'Add to Shelf',
      'Mark as',
      'Send',
      'Download',
      'Metadata',
      'More',
      'Delete',
    ]);

    permissions.set({...ALL_PERMISSIONS, canBulkResetGrimmoryReadProgress: false, canBulkResetKoReaderReadProgress: false, canMoveOrganizeFiles: false});
    fixture.componentRef.setInput('openInNewTab', false);
    expect(rowLabels()).toEqual(['Read Book', 'Add to Shelf', 'Mark as', 'Send', 'Download', 'Metadata', 'Delete']);
  });

  it('drops Send and Download without a digital file, keeping them for any format', () => {
    permissions.set(ALL_PERMISSIONS);

    showBook(makeBook({primaryFile: undefined}));
    expect(rowLabels()).toEqual(['Open Details in New Tab', 'Add to Shelf', 'Mark as', 'Metadata', 'More', 'Delete']);

    showBook(makeBook({
      primaryFile: undefined,
      alternativeFormats: [{id: 11, bookId: 1, book: true, folderBased: false, bookType: 'AUDIOBOOK'}],
    }));
    expect(rowLabels()).toEqual([
      'Open Details in New Tab',
      'Add to Shelf',
      'Mark as',
      'Send',
      'Download',
      'Metadata',
      'More',
      'Delete',
    ]);
    expect(submenuLabels('Send')).toEqual(['Custom Send']);

    showBook(null);
    expect(rowLabels()).toEqual(['Add to Shelf', 'Mark as', 'Metadata', 'More', 'Delete']);
  });

  it('hides Organize File on a non-local disk', () => {
    permissions.set(ALL_PERMISSIONS);
    expect(submenuLabels('More')).toContain('Organize File');

    diskType.set('REMOTE');
    expect(submenuLabels('More')).not.toContain('Organize File');
  });

  it('keeps confirmed shelf state when the book leaves the current result page', async () => {
    showBook(makeBook({
      shelves: [{id: 7, name: 'Reading List', publicShelf: false, bookCount: 1}],
    }));
    fixture.componentRef.setInput('books', []);

    (fixture.componentInstance as unknown as {
      onToggleShelf(shelfId: number, checked: boolean): void;
    }).onToggleShelf(7, false);

    await vi.waitFor(() => {
      const book = (fixture.componentInstance as unknown as {book(): BookSummary | null}).book();
      expect(book?.shelves).toEqual([]);
    });
  });

  it('restores the confirmed metadata-lock state after a failed command', async () => {
    const component = fixture.componentInstance as unknown as {
      metadataLocked: WritableSignal<boolean>;
      onMetadataLockChange(locked: boolean): void;
    };
    component.metadataLocked.set(true);
    component.onMetadataLockChange(true);

    await vi.waitFor(() => expect(component.metadataLocked()).toBe(false));
  });

  it('opens a file submenu only when the book has more than its primary file', () => {
    permissions.set(ALL_PERMISSIONS);

    expect(hasSubmenu('Download')).toBe(false);
    expect(hasSubmenu('Delete')).toBe(false);

    showBook(makeBook({
      primaryFile: {id: 10, bookId: 1, book: true, folderBased: false, fileName: 'warden.epub', fileSizeKb: 2048},
      alternativeFormats: [
        {id: 11, bookId: 1, book: true, folderBased: false, fileName: 'warden.pdf', fileSizeKb: 30720},
      ],
      supplementaryFiles: [
        {id: 12, bookId: 1, book: false, folderBased: false, fileName: 'warden.opf'},
      ],
    }));

    expect(hasSubmenu('Download')).toBe(true);
    expect(hasSubmenu('Delete')).toBe(true);
    expect(submenuLabels('Download')).toEqual(['warden.epub (2.00 MB)', 'warden.pdf (30.0 MB)', 'warden.opf']);
    expect(submenuLabels('Delete')).toEqual(['Book & all files', 'warden.pdf (30.0 MB)', 'warden.opf']);
  });
});
