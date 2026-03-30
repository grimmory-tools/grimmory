import {describe, expect, it} from 'vitest';

import {environment} from '../../../environments/environment';
import {API_CONFIG} from './api-config';

describe('API_CONFIG', () => {
  it('re-exports the environment API config object', () => {
    expect(API_CONFIG).toBe(environment.API_CONFIG);
  });

  it('exposes a defined config value', () => {
    expect(API_CONFIG).toBeDefined();
  });
});
