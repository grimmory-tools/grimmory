import {describe, expect, expectTypeOf, it} from 'vitest';

import {EmailProvider} from './email-provider.model';

describe('email-provider.model', () => {
  it('captures provider connection details and editing state', () => {
    const provider: EmailProvider = {
      isEditing: false,
      id: 1,
      userId: 9,
      name: 'SMTP',
      host: 'smtp.example.com',
      port: 587,
      username: 'user',
      password: 'secret',
      fromAddress: 'books@example.com',
      auth: true,
      startTls: true,
      defaultProvider: true,
      shared: false
    };

    expect(provider.port).toBe(587);
    expect(provider.defaultProvider).toBe(true);
    expectTypeOf(provider.startTls).toEqualTypeOf<boolean>();
  });
});
