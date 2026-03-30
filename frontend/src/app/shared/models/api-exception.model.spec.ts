import {describe, expect, expectTypeOf, it} from 'vitest';

import {APIException} from './api-exception.model';

describe('api-exception.model', () => {
  it('captures API exception payloads with optional details', () => {
    const error: APIException = {
      status: 500,
      message: 'Boom',
      timestamp: '2026-03-26T10:00:00Z',
      error: 'Internal Server Error'
    };

    expect(error.status).toBe(500);
    expect(error.message).toBe('Boom');
    expectTypeOf(error.timestamp).toEqualTypeOf<string | undefined>();
  });
});
