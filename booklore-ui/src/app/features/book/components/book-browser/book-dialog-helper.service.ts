import {inject, Injectable} from '@angular/core';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {DialogLauncherService, DialogSize, DialogStyle} from '../../../../shared/services/dialog-launcher.service';
import {ShelfAssignerComponent} from '../shelf-assigner/shelf-assigner.component';
import {LockUnlockMetadataDialogComponent} from './lock-unlock-metadata-dialog/lock-unlock-metadata-dialog.component';
import {MetadataRefreshType} from '../../../metadata/model/request/metadata-refresh-type.enum';
import {BulkMetadataUpdateComponent} from '../../../metadata/component/bulk-metadata-update/bulk-metadata-update-component';
import {MultiBookMetadataEditorComponent} from '../../../metadata/component/multi-book-metadata-editor/multi-book-metadata-editor-component';
import {MultiBookMetadataFetchComponent} from '../../../metadata/component/multi-book-metadata-fetch/multi-book-metadata-fetch-component';
import {FileMoverComponent} from '../../../../shared/components/file-mover/file-mover-component';
import {ShelfCreatorComponent} from '../shelf-creator/shelf-creator.component';
import {BookSenderComponent} from '../book-sender/book-sender.component';
import {MetadataFetchOptionsComponent} from '../../../metadata/component/metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';
import {BookMetadataCenterComponent} from '../../../metadata/component/book-metadata-center/book-metadata-center.component';
import {CoverSearchComponent} from '../../../metadata/component/cover-search/cover-search.component';
import {Book} from '../../model/book.model';
import {AdditionalFileUploaderComponent} from '../additional-file-uploader/additional-file-uploader.component';
import {BookFileAttacherComponent} from '../book-file-attacher/book-file-attacher.component';
import {AddPhysicalBookDialogComponent} from '../add-physical-book-dialog/add-physical-book-dialog.component';

@Injectable({providedIn: 'root'})
export class BookDialogHelperService {

  private dialogLauncherService = inject(DialogLauncherService);

  private openDialog(component: unknown, options: {}): DynamicDialogRef | null {
    return this.dialogLauncherService.openDialog(component, options);
  }

  openBookDetailsDialog(bookId: number): DynamicDialogRef | null {
    return this.openDialog(BookMetadataCenterComponent, {
      showHeader: false,
      styleClass: `book-details-dialog ${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
      data: {
        bookId: bookId,
      },
    });
  }

  openShelfAssignerDialog(book: Book | null, bookIds: Set<number> | null): DynamicDialogRef | null {
    const data: any = {};
    if (book !== null) {
      data.isMultiBooks = false;
      data.book = book;
    } else if (bookIds !== null) {
      data.isMultiBooks = true;
      data.bookIds = bookIds;
    } else {
      return null;
    }
    return this.openDialog(ShelfAssignerComponent, {
      showHeader: false,
      data: data,
      styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
    });
  }

  openShelfCreatorDialog(): DynamicDialogRef {
    return this.openDialog(ShelfCreatorComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
    })!;
  }

  openLockUnlockMetadataDialog(bookIds: Set<number>): DynamicDialogRef | null {
    return this.openDialog(LockUnlockMetadataDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
      data: {
        bookIds: Array.from(bookIds),
      },
    });
  }

  openMetadataRefreshDialog(bookIds: Set<number>): DynamicDialogRef | null {
    return this.openDialog(MultiBookMetadataFetchComponent, {
      showHeader: false,
      styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
      data: {
        bookIds: Array.from(bookIds),
        metadataRefreshType: MetadataRefreshType.BOOKS,
      },
    });
  }

  openBulkMetadataEditDialog(bookIds: Set<number>): DynamicDialogRef | null {
    return this.openDialog(BulkMetadataUpdateComponent, {
      showHeader: false,
      styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
      data: {
        bookIds: Array.from(bookIds),
      },
    });
  }

  openMultibookMetadataEditorDialog(bookIds: Set<number>): DynamicDialogRef | null {
    return this.openDialog(MultiBookMetadataEditorComponent, {
      showHeader: false,
      styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
      data: {
        bookIds: Array.from(bookIds),
      },
    });
  }

  openFileMoverDialog(bookIds: Set<number>): DynamicDialogRef | null {
    return this.openDialog(FileMoverComponent, {
      showHeader: false,
      styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
      maximizable: true,
      data: {
        bookIds: Array.from(bookIds),
      },
    });
  }

  openCustomSendDialog(bookId: number): DynamicDialogRef | null {
    return this.openDialog(BookSenderComponent, {
      showHeader: false,
      styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
      data: {
        bookId: bookId,
      },
    });
  }

  openCoverSearchDialog(bookId: number, coverType?: 'ebook' | 'audiobook'): DynamicDialogRef | null {
    return this.openDialog(CoverSearchComponent, {
      showHeader: false,
      styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
      data: {
        bookId: bookId,
        coverType: coverType,
      },
    });
  }

  openAdditionalFileUploaderDialog(book: Book): DynamicDialogRef | null {
    return this.openDialog(AdditionalFileUploaderComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      data: {
        book: book,
      },
    });
  }

  openBookFileAttacherDialog(sourceBook: Book): DynamicDialogRef | null {
    return this.openDialog(BookFileAttacherComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      data: {
        sourceBook: sourceBook,
      },
    });
  }

  openBulkBookFileAttacherDialog(sourceBooks: Book[]): DynamicDialogRef | null {
    return this.openDialog(BookFileAttacherComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      data: {
        sourceBooks: sourceBooks,
      },
    });
  }

  openAddPhysicalBookDialog(libraryId?: number): DynamicDialogRef | null {
    return this.openDialog(AddPhysicalBookDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
      data: {
        libraryId: libraryId,
      },
    });
  }
}
