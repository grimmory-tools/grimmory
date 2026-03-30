import {describe, expect, it} from 'vitest';

import * as metadataIndex from './index';
import {getTopFields} from './metadata-field.config';
import {hasMetadataWriter} from './embeddable-fields.config';

describe('shared metadata index', () => {
  it('re-exports metadata field helpers', () => {
    expect(metadataIndex.getTopFields).toBe(getTopFields);
  });

  it('re-exports embeddable field helpers', () => {
    expect(metadataIndex.hasMetadataWriter).toBe(hasMetadataWriter);
  });
});
