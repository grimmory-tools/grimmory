import {provideZonelessChangeDetection} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ActivatedRoute, Router} from '@angular/router';
import {of, Subject} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {AuditLog, AuditLogService, PageableResponse} from './audit-log.service';
import {AuditLogsComponent} from './audit-logs.component';

describe('AuditLogsComponent', () => {
  let fixture: ComponentFixture<AuditLogsComponent>;
  let usernameResponse: Subject<string[]>;
  const getAuditLogs = vi.fn();
  const getDistinctUsernames = vi.fn();

  beforeEach(async () => {
    usernameResponse = new Subject<string[]>();
    getAuditLogs
      .mockReset()
      .mockReturnValueOnce(of(createResponse('Initial audit log')))
      .mockReturnValueOnce(of(createResponse('Refreshed audit log')));
    getDistinctUsernames
      .mockReset()
      .mockReturnValue(usernameResponse.asObservable());

    await TestBed.configureTestingModule({
      imports: [AuditLogsComponent, getTranslocoModule()],
      providers: [
        provideZonelessChangeDetection(),
        {
          provide: AuditLogService,
          useValue: {
            getAuditLogs,
            getDistinctUsernames,
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {snapshot: {queryParams: {}}},
        },
        {
          provide: Router,
          useValue: {navigate: vi.fn()},
        },
      ],
    })
    .overrideComponent(AuditLogsComponent, {
      set: {
        template: `
          <span class="description-cell">{{ logs()[0]?.description }}</span>
          <span class="username-option">{{ usernameOptions()[1]?.label }}</span>
        `,
      },
    })
    .compileComponents();

    fixture = TestBed.createComponent(AuditLogsComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    usernameResponse.complete();
    TestBed.resetTestingModule();
  });

  it('renders audit-log updates from a background refresh', async () => {
    expect(fixture.nativeElement.querySelector('.description-cell').textContent.trim()).toBe('Initial audit log');

    fixture.componentInstance.loadLogs(false);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('.description-cell').textContent.trim()).toBe('Refreshed audit log');
  });

  it('renders username options loaded from the service', async () => {
    usernameResponse.next(['alice']);
    await fixture.whenStable();

    expect(getDistinctUsernames).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.username-option').textContent.trim()).toBe('alice');
  });
});

function createResponse(description: string): PageableResponse<AuditLog> {
  return {
    content: [{
      id: 1,
      userId: 1,
      username: 'alice',
      action: 'LOGIN_SUCCESS',
      entityType: null,
      entityId: null,
      description,
      ipAddress: '127.0.0.1',
      countryCode: 'US',
      createdAt: '2026-06-02T18:00:00Z',
    }],
    page: {
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 25,
    },
  };
}
