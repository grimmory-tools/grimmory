import {TestBed} from '@angular/core/testing';
import {firstValueFrom} from 'rxjs';
import {describe, expect, it, vi} from 'vitest';

import {PostLoginInitializerService} from './post-login-initializer.service';
import {StartupService} from '../../shared/service/startup.service';

describe('PostLoginInitializerService', () => {
  it('hydrates startup preferences after login', async () => {
    const load = vi.fn().mockResolvedValue(undefined);

    TestBed.configureTestingModule({
      providers: [
        PostLoginInitializerService,
        {provide: StartupService, useValue: {load}},
      ],
    });

    const service = TestBed.inject(PostLoginInitializerService);

    await expect(firstValueFrom(service.initialize())).resolves.toBeUndefined();
    expect(load).toHaveBeenCalledOnce();
  });
});
