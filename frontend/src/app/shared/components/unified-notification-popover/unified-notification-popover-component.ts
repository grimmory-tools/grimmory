import {Component, inject} from '@angular/core';
import {LiveNotificationBoxComponent} from '../live-notification-box/live-notification-box.component';
import {MetadataProgressService} from '../../service/metadata-progress.service';
import {map} from 'rxjs/operators';
import {toSignal} from '@angular/core/rxjs-interop';
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
    BookdropFilesWidgetComponent
  ],
  templateUrl: './unified-notification-popover-component.html',
  standalone: true,
  styleUrl: './unified-notification-popover-component.scss'
})
export class UnifiedNotificationBoxComponent {
  private readonly metadataProgressService = inject(MetadataProgressService);
  private readonly bookdropFileService = inject(BookdropFileService);
  private readonly libraryImportProgressService = inject(LibraryImportProgressService);

  protected readonly hasMetadataTasks = toSignal(
    this.metadataProgressService.activeTasks$.pipe(map(tasks => Object.keys(tasks).length > 0)),
    {initialValue: false}
  );

  protected readonly hasPendingBookdropFiles = toSignal(this.bookdropFileService.hasPendingFiles$, {initialValue: false});
  protected readonly hasActiveLibraryImport = this.libraryImportProgressService.hasActiveImport;
}
