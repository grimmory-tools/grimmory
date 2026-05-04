import {inject, Injectable} from '@angular/core';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {DialogLauncherService, DialogSize, DialogStyle} from '../../../../shared/services/dialog-launcher.service';
import {MetadataRefreshType} from '../../../metadata/model/request/metadata-refresh-type.enum';
import {Book} from '../../model/book.model';

@Injectable({providedIn: 'root'})
export class BookDialogHelperService {

  private dialogLauncherService = inject(DialogLauncherService);

  private openDialog(component: unknown, options: object): DynamicDialogRef | null {
    return this.dialogLauncherService.openDialog(component, options);
  }

  async openBookDetailsDialog(bookId: number): Promise<DynamicDialogRef | null> {
    const {BookMetadataCenterComponent} = await import('../../../metadata/component/book-metadata-center/book-metadata-center.component');
    return this.openDialog(BookMetadataCenterComponent, {
      showHeader: false,
      styleClass: `book-details-dialog ${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
      data: {
        bookId: bookId,
      },
    });
  }

  async openShelfAssignerDialog(book: Book | null, bookIds: Set<number> | null): Promise<DynamicDialogRef | null> {
    const data: { isMultiBooks: boolean; book?: Book; bookIds?: Set<number> } = {
      isMultiBooks: false
    };
    if (book !== null) {
      data.book = book;
    } else if (bookIds !== null) {
      data.isMultiBooks = true;
      data.bookIds = bookIds;
    } else {
      return null;
    }
    const {ShelfAssignerComponent} = await import('../shelf-assigner/shelf-assigner.component');
    return this.openDialog(ShelfAssignerComponent, {
      showHeader: false,
      data: data,
      styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
    });
  }

  async openShelfCreatorDialog(): Promise<DynamicDialogRef | null> {
    const {ShelfCreatorComponent} = await import('../shelf-creator/shelf-creator.component');
    return this.openDialog(ShelfCreatorComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
    });
  }

  async openLockUnlockMetadataDialog(bookIds: Set<number>): Promise<DynamicDialogRef | null> {
    const {LockUnlockMetadataDialogComponent} = await import('./lock-unlock-metadata-dialog/lock-unlock-metadata-dialog.component');
    return this.openDialog(LockUnlockMetadataDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
      data: {
        bookIds: Array.from(bookIds),
      },
    });
  }

  async openMetadataRefreshDialog(bookIds: Set<number>): Promise<DynamicDialogRef | null> {
    const {MultiBookMetadataFetchComponent} = await import('../../../metadata/component/multi-book-metadata-fetch/multi-book-metadata-fetch-component');
    return this.openDialog(MultiBookMetadataFetchComponent, {
      showHeader: false,
      styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
      data: {
        bookIds: Array.from(bookIds),
        metadataRefreshType: MetadataRefreshType.BOOKS,
      },
    });
  }

  async openBulkMetadataEditDialog(bookIds: Set<number>): Promise<DynamicDialogRef | null> {
    const {BulkMetadataUpdateComponent} = await import('../../../metadata/component/bulk-metadata-update/bulk-metadata-update-component');
    return this.openDialog(BulkMetadataUpdateComponent, {
      showHeader: false,
      styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
      data: {
        bookIds: Array.from(bookIds),
      },
    });
  }

  async openMultibookMetadataEditorDialog(bookIds: Set<number>): Promise<DynamicDialogRef | null> {
    const {MultiBookMetadataEditorComponent} = await import('../../../metadata/component/multi-book-metadata-editor/multi-book-metadata-editor-component');
    return this.openDialog(MultiBookMetadataEditorComponent, {
      showHeader: false,
      styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
      data: {
        bookIds: Array.from(bookIds),
      },
    });
  }

  async openFileMoverDialog(bookIds: Set<number>): Promise<DynamicDialogRef | null> {
    const {FileMoverComponent} = await import('../../../../shared/components/file-mover/file-mover-component');
    return this.openDialog(FileMoverComponent, {
      showHeader: false,
      styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
      maximizable: true,
      data: {
        bookIds: Array.from(bookIds),
      },
    });
  }

  async openCustomSendDialog(book: Book): Promise<DynamicDialogRef | null> {
    const {BookSenderComponent} = await import('../book-sender/book-sender.component');
    return this.openDialog(BookSenderComponent, {
      showHeader: false,
      styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
      data: {
        book: book,
      },
    });
  }

  async openCoverSearchDialog(bookId: number, coverType?: 'ebook' | 'audiobook'): Promise<DynamicDialogRef | null> {
    const {CoverSearchComponent} = await import('../../../metadata/component/cover-search/cover-search.component');
    return this.openDialog(CoverSearchComponent, {
      showHeader: false,
      styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
      data: {
        bookId: bookId,
        coverType: coverType,
      },
    });
  }

  async openAdditionalFileUploaderDialog(book: Book): Promise<DynamicDialogRef | null> {
    const {AdditionalFileUploaderComponent} = await import('../additional-file-uploader/additional-file-uploader.component');
    return this.openDialog(AdditionalFileUploaderComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      data: {
        book: book,
      },
    });
  }

  async openBookFileAttacherDialog(sourceBook: Book): Promise<DynamicDialogRef | null> {
    const {BookFileAttacherComponent} = await import('../book-file-attacher/book-file-attacher.component');
    return this.openDialog(BookFileAttacherComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      data: {
        sourceBook: sourceBook,
      },
    });
  }

  async openBulkBookFileAttacherDialog(sourceBooks: Book[]): Promise<DynamicDialogRef | null> {
    const {BookFileAttacherComponent} = await import('../book-file-attacher/book-file-attacher.component');
    return this.openDialog(BookFileAttacherComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      data: {
        sourceBooks: sourceBooks,
      },
    });
  }

  async openDuplicateMergerDialog(libraryId: number): Promise<DynamicDialogRef | null> {
    const {DuplicateMergerComponent} = await import('../duplicate-merger/duplicate-merger.component');
    return this.openDialog(DuplicateMergerComponent, {
      showHeader: false,
      styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
      data: {
        libraryId: libraryId,
      },
    });
  }

  async openAddPhysicalBookDialog(libraryId?: number): Promise<DynamicDialogRef | null> {
    const {AddPhysicalBookDialogComponent} = await import('../add-physical-book-dialog/add-physical-book-dialog.component');
    return this.openDialog(AddPhysicalBookDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
      data: {
        libraryId: libraryId,
      },
    });
  }

  async openBulkIsbnImportDialog(libraryId?: number): Promise<DynamicDialogRef | null> {
    const {BulkIsbnImportDialogComponent} = await import('../bulk-isbn-import-dialog/bulk-isbn-import-dialog.component');
    return this.openDialog(BulkIsbnImportDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
      data: {
        libraryId: libraryId,
      },
    });
  }
}
