import {inject, Injectable} from '@angular/core';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {ShelfAssignerComponent} from '../shelf-assigner/shelf-assigner.component';
import {LockUnlockMetadataDialogComponent} from './lock-unlock-metadata-dialog/lock-unlock-metadata-dialog.component';
import {MetadataRefreshType} from '../../../metadata/model/request/metadata-refresh-type.enum';
import {BulkMetadataUpdateComponent} from '../../../metadata/component/bulk-metadata-update/bulk-metadata-update-component';
import {MultiBookMetadataEditorComponent} from '../../../metadata/component/multi-book-metadata-editor/multi-book-metadata-editor-component';
import {MultiBookMetadataFetchComponent} from '../../../metadata/component/multi-book-metadata-fetch/multi-book-metadata-fetch-component';
import {FileMoverComponent} from '../../../../shared/components/file-mover/file-mover-component';

@Injectable({providedIn: 'root'})
export class BookDialogHelperService {

  private dialogService = inject(DialogService);

  openShelfAssigner(bookIds: Set<number>): DynamicDialogRef | null {
    return this.dialogService.open(ShelfAssignerComponent, {
      header: `Update Books' Shelves`,
      modal: true,
      closable: true,
      contentStyle: {overflow: 'auto'},
      baseZIndex: 10,
      style: {
        position: 'absolute',
        top: '15%',
      },
      data: {
        isMultiBooks: true,
        bookIds,
      },
    });
  }

  openLockUnlockMetadataDialog(bookIds: Set<number>): DynamicDialogRef | null {
    const count = bookIds.size;
    return this.dialogService.open(LockUnlockMetadataDialogComponent, {
      header: `Lock or Unlock Metadata for ${count} Selected Book${count > 1 ? 's' : ''}`,
      modal: true,
      closable: true,
      data: {
        bookIds: Array.from(bookIds),
      },
    });
  }

  openMetadataRefreshDialog(bookIds: Set<number>): DynamicDialogRef | null {
    return this.dialogService.open(MultiBookMetadataFetchComponent, {
      header: 'Metadata Refresh Options',
      modal: true,
      closable: true,
      data: {
        bookIds: Array.from(bookIds),
        metadataRefreshType: MetadataRefreshType.BOOKS,
      },
    });
  }

  openBulkMetadataEditDialog(bookIds: Set<number>): DynamicDialogRef | null {
    return this.dialogService.open(BulkMetadataUpdateComponent, {
      header: 'Bulk Edit Metadata',
      modal: true,
      closable: true,
      style: {
        width: '90vw',
        maxWidth: '1200px',
        position: 'absolute'
      },
      data: {
        bookIds: Array.from(bookIds)
      },
    });
  }

  openMultibookMetadataEditorDialog(bookIds: Set<number>): DynamicDialogRef | null {
    return this.dialogService.open(MultiBookMetadataEditorComponent, {
      header: 'Bulk Edit Metadata',
      showHeader: false,
      modal: true,
      closable: true,
      closeOnEscape: true,
      dismissableMask: true,
      style: {
        width: '95vw',
        overflow: 'none',
      },
      data: {
        bookIds: Array.from(bookIds)
      },
    });
  }

  openFileMoverDialog(selectedBooks: Set<number>) {
    const count = selectedBooks.size;
    return this.dialogService.open(FileMoverComponent, {
      header: `Organize Book Files (${count} book${count !== 1 ? 's' : ''})`,
      showHeader: true,
      maximizable: true,
      modal: true,
      closable: true,
      closeOnEscape: false,
      dismissableMask: false,
      style: {
        width: '95vw',
        maxWidth: '97.5vw',
        height: '90vh',
        maxHeight: '95vh'
      },
      data: {
        bookIds: selectedBooks
      },
    });
  }
}
