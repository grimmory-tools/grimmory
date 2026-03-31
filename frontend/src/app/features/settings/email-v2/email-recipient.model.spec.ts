import {describe, expect, expectTypeOf, it} from 'vitest';

import {EmailRecipient} from './email-recipient.model';

describe('email-recipient.model', () => {
  it('captures recipient identity and editing state', () => {
    const recipient: EmailRecipient = {
      id: 2,
      email: 'reader@example.com',
      name: 'Reader',
      defaultRecipient: true,
      isEditing: false
    };

    expect(recipient.email).toBe('reader@example.com');
    expect(recipient.defaultRecipient).toBe(true);
    expectTypeOf(recipient.isEditing).toEqualTypeOf<boolean>();
  });
});
