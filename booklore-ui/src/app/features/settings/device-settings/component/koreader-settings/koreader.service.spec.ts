import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {KoreaderService, KoreaderUser} from './koreader.service';

describe('KoreaderService', () => {
  let service: KoreaderService;
  let httpClientMock: any;

  const mockUser: KoreaderUser = {
    username: 'testuser',
    password: 'secret',
    syncEnabled: true,
    syncWithBookloreReader: false
  };

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      put: vi.fn(),
      patch: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        KoreaderService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(KoreaderService));
  });

  it('should create user', () => {
    httpClientMock.put.mockReturnValue(of(mockUser));
    service.createUser('testuser', 'secret').subscribe(user => {
      expect(user).toEqual(mockUser);
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.stringContaining('/me'),
        {username: 'testuser', password: 'secret'}
      );
    });
  });

  it('should get user', () => {
    httpClientMock.get.mockReturnValue(of(mockUser));
    service.getUser().subscribe(user => {
      expect(user).toEqual(mockUser);
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringContaining('/me'));
    });
  });

  it('should toggle sync', () => {
    httpClientMock.patch.mockReturnValue(of(void 0));
    service.toggleSync(true).subscribe(result => {
      expect(result).toBeUndefined();
      expect(httpClientMock.patch).toHaveBeenCalledWith(
        expect.stringContaining('/me/sync'),
        null,
        {params: {enabled: 'true'}}
      );
    });
  });

  it('should handle createUser error', () => {
    httpClientMock.put.mockReturnValue(throwError(() => new Error('fail')));
    service.createUser('testuser', 'secret').subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle getUser error', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service.getUser().subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });

  it('should handle toggleSync error', () => {
    httpClientMock.patch.mockReturnValue(throwError(() => new Error('fail')));
    service.toggleSync(false).subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
        expect(err.message).toBe('fail');
      }
    });
  });
});

describe('KoreaderService - API Contract Tests', () => {
  let service: KoreaderService;
  let httpClientMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      put: vi.fn(),
      patch: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        KoreaderService,
        {provide: HttpClient, useValue: httpClientMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(KoreaderService));
  });

  it('should validate all required KoreaderUser fields exist', () => {
    const requiredFields: (keyof KoreaderUser)[] = ['username', 'password', 'syncEnabled', 'syncWithBookloreReader'];
    const mockResponse: KoreaderUser = {
      username: 'test',
      password: 'pw',
      syncEnabled: true,
      syncWithBookloreReader: false
    };
    httpClientMock.get.mockReturnValue(of(mockResponse));
    service.getUser().subscribe(user => {
      requiredFields.forEach(field => {
        expect(user).toHaveProperty(field);
        expect(user[field]).toBeDefined();
      });
    });
  });

  it('should fail if API returns KoreaderUser without required username field', () => {
    const invalidResponse = {
      password: 'pw',
      syncEnabled: true
    };
    httpClientMock.get.mockReturnValue(of(invalidResponse));
    service.getUser().subscribe(user => {
      expect(user).not.toHaveProperty('username');
    });
  });

  it('should fail if API returns KoreaderUser without required password field', () => {
    const invalidResponse = {
      username: 'test',
      syncEnabled: true
    };
    httpClientMock.get.mockReturnValue(of(invalidResponse));
    service.getUser().subscribe(user => {
      expect(user).not.toHaveProperty('password');
    });
  });

  it('should fail if API returns KoreaderUser without required syncEnabled field', () => {
    const invalidResponse = {
      username: 'test',
      password: 'pw'
    };
    httpClientMock.get.mockReturnValue(of(invalidResponse));
    service.getUser().subscribe(user => {
      expect(user).not.toHaveProperty('syncEnabled');
    });
  });

  it('should call correct endpoint for createUser', () => {
    httpClientMock.put.mockReturnValue(of({}));
    service.createUser('u', 'p').subscribe();
    expect(httpClientMock.put).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/koreader-users\/me$/),
      {username: 'u', password: 'p'}
    );
  });

  it('should call correct endpoint for getUser', () => {
    httpClientMock.get.mockReturnValue(of({}));
    service.getUser().subscribe();
    expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/koreader-users\/me$/));
  });

  it('should call correct endpoint for toggleSync', () => {
    httpClientMock.patch.mockReturnValue(of({}));
    service.toggleSync(true).subscribe();
    expect(httpClientMock.patch).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/koreader-users\/me\/sync$/),
      null,
      {params: {enabled: 'true'}}
    );
  });
});
