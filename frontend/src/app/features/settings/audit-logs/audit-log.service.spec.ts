import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {AuditLog, AuditLogService, PageableResponse} from './audit-log.service';

describe('AuditLogService', () => {
  let service: AuditLogService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AuditLogService,
      ],
    });

    service = TestBed.inject(AuditLogService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('requests paged audit logs with the default pagination', () => {
    const response: PageableResponse<AuditLog> = {
      content: [],
      page: {
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 25,
      },
    };

    let result: PageableResponse<AuditLog> | undefined;
    service.getAuditLogs().subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/audit-logs'));
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('25');
    expect(request.request.params.has('action')).toBe(false);
    expect(request.request.params.has('username')).toBe(false);
    expect(request.request.params.has('from')).toBe(false);
    expect(request.request.params.has('to')).toBe(false);
    request.flush(response);

    expect(result).toEqual(response);
  });

  it('includes optional filters when they are provided', () => {
    service.getAuditLogs(2, 50, 'DELETE', 'alice', '2026-01-01', '2026-01-31').subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/audit-logs'));
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');
    expect(request.request.params.get('action')).toBe('DELETE');
    expect(request.request.params.get('username')).toBe('alice');
    expect(request.request.params.get('from')).toBe('2026-01-01');
    expect(request.request.params.get('to')).toBe('2026-01-31');
    request.flush({
      content: [],
      page: {
        totalElements: 0,
        totalPages: 0,
        number: 2,
        size: 50,
      },
    } satisfies PageableResponse<AuditLog>);
  });

  it('requests distinct usernames from the dedicated endpoint', () => {
    const response = ['alice', 'bob'];

    let result: string[] | undefined;
    service.getDistinctUsernames().subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/audit-logs/usernames'));
    expect(request.request.method).toBe('GET');
    request.flush(response);

    expect(result).toEqual(response);
  });
});
