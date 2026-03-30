import {describe, expect, it} from 'vitest';

import {passwordMatchValidator} from './password-match.validator';

describe('passwordMatchValidator', () => {
  it('returns null until both values are present', () => {
    const validator = passwordMatchValidator('password', 'confirmPassword');
    const form = {
      get: (name: string) => ({value: name === 'password' ? '' : ''}),
    };

    expect(validator(form as never)).toBeNull();

    const withPassword = {
      get: (name: string) => ({value: name === 'password' ? 'secret' : ''}),
    };
    expect(validator(withPassword as never)).toBeNull();
  });

  it('flags mismatched passwords', () => {
    const validator = passwordMatchValidator('password', 'confirmPassword');
    const form = {
      get: (name: string) => ({value: name === 'password' ? 'secret' : 'other'}),
    };

    expect(validator(form as never)).toEqual({passwordMismatch: true});
  });

  it('returns null for matching passwords', () => {
    const validator = passwordMatchValidator('password', 'confirmPassword');
    const form = {
      get: (name: string) => ({value: name === 'password' ? 'secret' : 'secret'}),
    };

    expect(validator(form as never)).toBeNull();
  });
});
