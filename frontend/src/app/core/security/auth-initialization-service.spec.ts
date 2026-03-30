import {TestBed} from '@angular/core/testing';
import {describe, expect, it} from 'vitest';

import {AuthInitializationService} from './auth-initialization-service';

describe('AuthInitializationService', () => {
  it('starts in an uninitialized state and can be marked as initialized', () => {
    TestBed.configureTestingModule({
      providers: [AuthInitializationService],
    });

    const service = TestBed.inject(AuthInitializationService);

    expect(service.initialized()).toBe(false);

    service.markAsInitialized();

    expect(service.initialized()).toBe(true);
  });
});
