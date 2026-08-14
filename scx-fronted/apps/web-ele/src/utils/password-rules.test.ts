import { describe, expect, it } from 'vitest';

import { getPasswordStrengthError } from './password-rules';

describe('password strength rules', () => {
  it('accepts the shared letter, digit and special-character policy', () => {
    expect(getPasswordStrengthError('Abcd1!')).toBeNull();
    expect(getPasswordStrengthError('密码1€ab')).toBeNull();
  });

  it('rejects whitespace and each missing character class', () => {
    expect(getPasswordStrengthError('Ab1! x')).toBe('密码不能包含空白字符');
    expect(getPasswordStrengthError('12345!')).toBe('密码需包含字母');
    expect(getPasswordStrengthError('Abcdef!')).toBe('密码需包含数字');
    expect(getPasswordStrengthError('Abc123')).toBe('密码需包含特殊字符');
  });

  it('handles required and optional empty values', () => {
    expect(getPasswordStrengthError('')).toBe('请输入密码');
    expect(getPasswordStrengthError('', { required: false })).toBeNull();
  });
});
