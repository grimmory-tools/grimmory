import {describe, expect, expectTypeOf, it} from 'vitest';

import {OidcGroupMapping} from './oidc-group-mapping.model';

describe('oidc-group-mapping.model', () => {
  it('supports group claims with library-specific permissions', () => {
    const mapping: OidcGroupMapping = {
      id: 1,
      oidcGroupClaim: 'admins',
      isAdmin: true,
      permissions: ['books.read', 'books.write'],
      libraryIds: [10, 11],
      description: 'Admin access'
    };

    expect(mapping.libraryIds).toEqual([10, 11]);
    expect(mapping.isAdmin).toBe(true);
    expectTypeOf(mapping.permissions).toEqualTypeOf<string[]>();
  });
});
