import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {LiveNotificationBoxComponent} from '../live-notification-box/live-notification-box.component';
import {MetadataProgressService} from '../../service/metadata-progress.service';
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
  styleUrl: './unified-notification-popover-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UnifiedNotificationBoxComponent {
  private readonly metadataProgressService = inject(MetadataProgressService);
  private readonly bookdropFileService = inject(BookdropFileService);
  private readonly libraryImportProgressService = inject(LibraryImportProgressService);

  private readonly activeMetadataTasks = toSignal(this.metadataProgressService.activeTasks$, {initialValue: {}});
  protected readonly hasMetadataTasks = computed(() => Object.keys(this.activeMetadataTasks()).length > 0);
  protected readonly hasPendingBookdropFiles = toSignal(this.bookdropFileService.hasPendingFiles$, {initialValue: false});
  protected readonly hasActiveLibraryImport = this.libraryImportProgressService.hasActiveImport;
}
