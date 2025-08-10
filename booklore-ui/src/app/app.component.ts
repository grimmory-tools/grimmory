import {Component, inject, OnInit} from '@angular/core';
import {RxStompService} from './shared/websocket/rx-stomp.service';
import {Message} from '@stomp/stompjs';
import {BookService} from './book/service/book.service';
import {NotificationEventService} from './shared/websocket/notification-event.service';
import {parseLogNotification, parseTaskMessage, TaskMessage} from './shared/websocket/model/log-notification.model';
import {ConfirmDialog} from 'primeng/confirmdialog';
import {Toast} from 'primeng/toast';
import {RouterOutlet} from '@angular/router';
import {AuthInitializationService} from './auth-initialization-service';
import {AppConfigService} from './core/service/app-config.service';
import {MetadataBatchProgressNotification} from './core/model/metadata-batch-progress.model';
import {MetadataProgressService} from './core/service/metadata-progress-service';
import {BookdropFileService, BookdropFileNotification} from './bookdrop/bookdrop-file.service';
import {TaskEventService} from './shared/websocket/task-event.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  standalone: true,
  imports: [ConfirmDialog, Toast, RouterOutlet]
})
export class AppComponent implements OnInit {

  loading = true;
  private authInit = inject(AuthInitializationService);
  private bookService = inject(BookService);
  private rxStompService = inject(RxStompService);
  private notificationEventService = inject(NotificationEventService);
  private metadataProgressService = inject(MetadataProgressService);
  private bookdropFileService = inject(BookdropFileService);
  private taskEventService = inject(TaskEventService);
  private appConfigService = inject(AppConfigService);

  ngOnInit(): void {

    this.authInit.initialized$.subscribe(ready => {
      this.loading = !ready;
    });

    this.rxStompService.watch('/topic/book-add').subscribe((message: Message) => {
      this.bookService.handleNewlyCreatedBook(JSON.parse(message.body));
    });

    this.rxStompService.watch('/topic/books-remove').subscribe((message: Message) => {
      this.bookService.handleRemovedBookIds(JSON.parse(message.body));
    });

    this.rxStompService.watch('/topic/book-metadata-update').subscribe((message: Message) => {
      this.bookService.handleBookUpdate(JSON.parse(message.body));
    });

    this.rxStompService.watch('/topic/book-metadata-batch-update').subscribe((message: Message) => {
      const updatedBooks = JSON.parse(message.body);
      this.bookService.handleMultipleBookUpdates(updatedBooks);
    });

    this.rxStompService.watch('/topic/book-metadata-batch-progress').subscribe((message: Message) => {
      const progress = JSON.parse(message.body) as MetadataBatchProgressNotification;
      this.metadataProgressService.handleIncomingProgress(progress);
    });

    this.rxStompService.watch('/topic/log').subscribe((message: Message) => {
      const logNotification = parseLogNotification(message.body);
      this.notificationEventService.handleNewNotification(logNotification);
    });

    this.rxStompService.watch('/topic/task').subscribe((message: Message) => {
      const taskMessage: TaskMessage = parseTaskMessage(message.body);
      this.taskEventService.handleTaskMessage(taskMessage);
    });

    this.rxStompService.watch('/topic/bookdrop-file').subscribe((message: Message) => {
      const notification = JSON.parse(message.body) as BookdropFileNotification;
      this.bookdropFileService.handleIncomingFile(notification);
    });
  }
}
