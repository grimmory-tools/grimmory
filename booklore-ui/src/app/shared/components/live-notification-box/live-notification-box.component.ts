import {Component, inject} from '@angular/core';
import {NotificationEventService} from '../../websocket/notification-event.service';
import {LogNotification} from '../../websocket/model/log-notification.model';
import {Tag} from 'primeng/tag';
import {TranslocoService} from '@jsverse/transloco';

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
export class LiveNotificationBoxComponent {
  private readonly t = inject(TranslocoService);
  latestNotification: LogNotification = {message: this.t.translate('shared.liveNotification.defaultMessage')};

  private notificationService = inject(NotificationEventService);

  constructor() {
    this.notificationService.latestNotification$.subscribe(notification => {
      this.latestNotification = notification;
    });
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
