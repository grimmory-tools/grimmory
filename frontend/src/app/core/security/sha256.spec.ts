import {describe, expect, it} from 'vitest';

import {sha256} from './sha256';

const toHex = (bytes: Uint8Array) => Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
const utf8 = (value: string) => new TextEncoder().encode(value);

describe('sha256', () => {
  it('matches the FIPS 180-4 test vectors', () => {
    expect(toHex(sha256(utf8('')))).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
    expect(toHex(sha256(utf8('abc')))).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
    expect(toHex(sha256(utf8('abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq'))))
      .toBe('248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1');
    expect(toHex(sha256(utf8('a'.repeat(1_000_000)))))
      .toBe('cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0');
  });

  it('hashes non-ASCII input by its UTF-8 bytes', () => {
    expect(toHex(sha256(utf8('\u00e9')))).toBe('4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c');
    expect(toHex(sha256(utf8('\u65e5\u672c\u8a9e \u{1F642}'))))
      .toBe('dc77b39d398d4566e7bbb539dcba35a4a985da4c5bf001f70f9e46016f5c1932');
  });

  it('agrees with WebCrypto for every length around the block boundaries', async () => {
    for (let length = 0; length <= 200; length++) {
      const data = Uint8Array.from({length}, (_, index) => (index * 31 + length) & 0xff);
      const expected = new Uint8Array(await globalThis.crypto.subtle.digest('SHA-256', data));
      expect(toHex(sha256(data)), `length ${length}`).toBe(toHex(expected));
    }
  });
});
