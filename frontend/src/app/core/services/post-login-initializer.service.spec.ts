import {TestBed} from '@angular/core/testing';
import {firstValueFrom} from 'rxjs';
import {describe, expect, it} from 'vitest';

import {PostLoginInitializerService} from './post-login-initializer.service';

describe('PostLoginInitializerService', () => {
  it('completes successfully', async () => {
    TestBed.configureTestingModule({
      providers: [PostLoginInitializerService],
    });

    const service = TestBed.inject(PostLoginInitializerService);

    await expect(firstValueFrom(service.initialize())).resolves.toBeUndefined();
  });
});
