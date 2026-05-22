import {Component, inject} from '@angular/core';
import {LiveNotificationBoxComponent} from '../live-notification-box/live-notification-box.component';
import {MetadataProgressService} from '../../service/metadata-progress.service';
import {map} from 'rxjs/operators';
import {AsyncPipe} from '@angular/common';
import {BookdropFileService} from '../../../features/bookdrop/service/bookdrop-file.service';
import {BookdropFilesWidgetComponent} from '../../../features/bookdrop/component/bookdrop-files-widget/bookdrop-files-widget.component';
import {MetadataProgressWidgetComponent} from '../metadata-progress-widget/metadata-progress-widget-component';
import {LibraryImportProgressService} from '../../service/library-import-progress.service';
import {LibraryImportProgressWidgetComponent} from '../library-import-progress-widget/library-import-progress-widget-component';

@Component({
  selector: 'app-unified-notification-popover-component',
  imports: [
    LiveNotificationBoxComponent,
    MetadataProgressWidgetComponent,
    LibraryImportProgressWidgetComponent,
    AsyncPipe,
    BookdropFilesWidgetComponent
  ],
  templateUrl: './unified-notification-popover-component.html',
  standalone: true,
  styleUrl: './unified-notification-popover-component.scss'
})
export class UnifiedNotificationBoxComponent {
  metadataProgressService = inject(MetadataProgressService);
  bookdropFileService = inject(BookdropFileService);
  libraryImportProgressService = inject(LibraryImportProgressService);

  hasMetadataTasks$ = this.metadataProgressService.activeTasks$.pipe(
    map(tasks => Object.keys(tasks).length > 0)
  );

  hasPendingBookdropFiles$ = this.bookdropFileService.hasPendingFiles$;
  hasActiveLibraryImport$ = this.libraryImportProgressService.hasActiveImport$;
}
