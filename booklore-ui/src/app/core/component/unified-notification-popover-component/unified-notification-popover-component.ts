import {Component, inject} from '@angular/core';
import {LiveNotificationBoxComponent} from '../live-notification-box/live-notification-box.component';
import {LiveTaskEventBoxComponent} from '../live-task-event-box/live-task-event-box.component';
import {MetadataProgressWidgetComponent} from '../metadata-progress-widget-component/metadata-progress-widget-component';
import {MetadataProgressService} from '../../service/metadata-progress-service';
import {TaskEventService} from '../../../shared/websocket/task-event.service';
import {map} from 'rxjs/operators';
import {AsyncPipe} from '@angular/common';
import {BookdropFilesWidgetComponent} from '../../../bookdrop/bookdrop-files-widget-component/bookdrop-files-widget.component';
import {BookdropFileService} from '../../../bookdrop/bookdrop-file.service';
import {DuplicateFilesNotificationComponent} from '../duplicate-files-notification/duplicate-files-notification.component';
import {DuplicateFileService} from '../../../shared/websocket/duplicate-file.service';

@Component({
  selector: 'app-unified-notification-popover-component',
  imports: [
    LiveNotificationBoxComponent,
    LiveTaskEventBoxComponent,
    MetadataProgressWidgetComponent,
    AsyncPipe,
    BookdropFilesWidgetComponent,
    DuplicateFilesNotificationComponent
  ],
  templateUrl: './unified-notification-popover-component.html',
  standalone: true,
  styleUrl: './unified-notification-popover-component.scss'
})
export class UnifiedNotificationBoxComponent {
  metadataProgressService = inject(MetadataProgressService);
  bookdropFileService = inject(BookdropFileService);
  duplicateFileService = inject(DuplicateFileService);
  taskEventService = inject(TaskEventService);

  hasMetadataTasks$ = this.metadataProgressService.activeTasks$.pipe(
    map(tasks => Object.keys(tasks).length > 0)
  );

  hasPendingBookdropFiles$ = this.bookdropFileService.hasPendingFiles$;

  hasDuplicateFiles$ = this.duplicateFileService.duplicateFiles$.pipe(
    map(files => files && files.length > 0)
  );

  hasActiveTasks$ = this.taskEventService.tasks$.pipe(
    map(tasks => tasks.length > 0)
  );
}
