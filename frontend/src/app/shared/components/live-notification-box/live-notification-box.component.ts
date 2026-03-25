import {Component, inject, OnDestroy} from '@angular/core';
import {NotificationEventService} from '../../websocket/notification-event.service';
import {LogNotification} from '../../websocket/model/log-notification.model';
import {Tag} from 'primeng/tag';
import {TranslocoService} from '@jsverse/transloco';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';

import {TagComponent} from '../tag/tag.component';

@Component({
  selector: 'app-live-notification-box',
  standalone: true,
  templateUrl: './live-notification-box.component.html',
  styleUrls: ['./live-notification-box.component.scss'],
  host: {
    class: 'config-panel'
  },
  imports: [
    TagComponent
  ]
})
export class LiveNotificationBoxComponent implements OnDestroy {
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();
  latestNotification: LogNotification = {message: this.t.translate('shared.liveNotification.defaultMessage')};
  private hasReceivedNotification = false;

  private notificationService = inject(NotificationEventService);

  constructor() {
    this.notificationService.latestNotification$.pipe(takeUntil(this.destroy$)).subscribe(notification => {
      this.hasReceivedNotification = true;
      this.latestNotification = notification;
    });
    this.t.langChanges$.pipe(takeUntil(this.destroy$)).subscribe(() => {
      if (!this.hasReceivedNotification) {
        this.latestNotification = {message: this.t.translate('shared.liveNotification.defaultMessage')};
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  getSeverityColor(severity?: string): 'red' | 'amber' | 'green' | 'gray' {
    switch (severity) {
      case 'ERROR':
        return 'red';
      case 'WARN':
        return 'amber';
      case 'INFO':
        return 'green';
      default:
        return 'gray';
    }
  }
}
