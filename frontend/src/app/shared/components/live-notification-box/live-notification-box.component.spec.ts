import {Subject} from 'rxjs';
import {TestBed, ComponentFixture} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';

import {NotificationEventService} from '../../websocket/notification-event.service';
import {LogNotification, Severity} from '../../websocket/model/log-notification.model';
import {LiveNotificationBoxComponent} from './live-notification-box.component';

describe('LiveNotificationBoxComponent', () => {
  let notifications$: Subject<LogNotification>;
  let langChanges$: Subject<string>;
  let component: LiveNotificationBoxComponent;
  let fixture: ComponentFixture<LiveNotificationBoxComponent>;

  beforeEach(() => {
    notifications$ = new Subject<LogNotification>();
    langChanges$ = new Subject<string>();

    TestBed.configureTestingModule({
      imports: [LiveNotificationBoxComponent],
      providers: [
        {
          provide: NotificationEventService,
          useValue: {latestNotification$: notifications$.asObservable()},
        },
        {
          provide: TranslocoService,
          useValue: {
            translate: (key: string) => `translated:${key}`,
            langChanges$: langChanges$.asObservable(),
          },
        },
      ],
    });

    fixture = TestBed.createComponent(LiveNotificationBoxComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('starts with the translated default message', () => {
    expect(component.latestNotification).toEqual({
      message: 'translated:shared.liveNotification.defaultMessage',
    });
  });

  it('replaces the latest notification when a websocket event arrives', () => {
    notifications$.next({message: 'Metadata updated', severity: Severity.INFO});

    expect(component.latestNotification).toEqual({
      message: 'Metadata updated',
      severity: Severity.INFO,
    });
  });

  it('refreshes the default message on language changes until a notification arrives', () => {
    langChanges$.next('fr');

    expect(component.latestNotification).toEqual({
      message: 'translated:shared.liveNotification.defaultMessage',
    });

    notifications$.next({message: 'Build finished', severity: Severity.WARN});
    langChanges$.next('de');

    expect(component.latestNotification).toEqual({
      message: 'Build finished',
      severity: Severity.WARN,
    });
  });

  it('maps severities to the expected tag colors', () => {
    expect(component.getSeverityColor(Severity.ERROR)).toBe('red');
    expect(component.getSeverityColor(Severity.WARN)).toBe('amber');
    expect(component.getSeverityColor(Severity.INFO)).toBe('green');
    expect(component.getSeverityColor(undefined)).toBe('gray');
  });

  it('stops reacting to streams after destruction', () => {
    component.ngOnDestroy();
    notifications$.next({message: 'Ignored', severity: Severity.ERROR});
    langChanges$.next('es');

    expect(component.latestNotification).toEqual({
      message: 'translated:shared.liveNotification.defaultMessage',
    });
  });
});
