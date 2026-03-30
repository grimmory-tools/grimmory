import {describe, expect, expectTypeOf, it} from 'vitest';

import {Library, LibraryPath, MetadataSource, OrganizationMode} from './library.model';

describe('library.model', () => {
  it('supports a library with paths and optional metadata settings', () => {
    const library: Library = {
      id: 1,
      name: 'Main Library',
      watch: true,
      paths: [{id: 10, path: '/books'}],
      metadataSource: 'PREFER_EMBEDDED',
      organizationMode: 'AUTO_DETECT'
    };

    expect(library.paths[0].path).toBe('/books');
    expect(library.metadataSource).toBe('PREFER_EMBEDDED');
    expectTypeOf(library.paths).toMatchTypeOf<LibraryPath[]>();
  });

  it('keeps metadata source and organization mode unions constrained', () => {
    expectTypeOf<MetadataSource>().toEqualTypeOf<
      'EMBEDDED' | 'SIDECAR' | 'PREFER_SIDECAR' | 'PREFER_EMBEDDED' | 'NONE'
    >();
    expectTypeOf<OrganizationMode>().toEqualTypeOf<
      'BOOK_PER_FILE' | 'BOOK_PER_FOLDER' | 'AUTO_DETECT'
    >();
  });
});
