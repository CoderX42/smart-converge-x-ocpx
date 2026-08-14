/** 与前端表单一致的密码策略（长度 + 复杂度），后端若校验请保持同步 */

export const PASSWORD_MIN_LEN = 6;
export const PASSWORD_MAX_LEN = 32;

/** 提示文案，用于表单项下方说明 */
export const PASSWORD_COMPLEXITY_HINT = `密码 ${PASSWORD_MIN_LEN}-${PASSWORD_MAX_LEN} 位，不含空白字符，需同时包含字母、数字和特殊字符（如 !@#$%^&*. 等）`;

const HAS_LETTER = /\p{Letter}/u;
const HAS_DIGIT = /\p{Decimal_Number}/u;
const HAS_WHITESPACE = /\p{White_Space}/u;
const HAS_SPECIAL = /[^\p{Decimal_Number}\p{Letter}\p{White_Space}]/u;

export type PasswordStrengthOptions = {
  /** 是否必填（空串视为不通过） */
  required?: boolean;
};

/**
 * @returns 错误信息字符串，通过则返回 null
 */
export function getPasswordStrengthError(
  value: null | string | undefined,
  options: PasswordStrengthOptions = {},
): null | string {
  const { required = true } = options;
  const v = value === null ? '' : String(value);

  if (!v.trim()) {
    return required ? '请输入密码' : null;
  }

  if (v.length < PASSWORD_MIN_LEN || v.length > PASSWORD_MAX_LEN) {
    return `密码长度需在 ${PASSWORD_MIN_LEN}-${PASSWORD_MAX_LEN} 位之间`;
  }

  if (HAS_WHITESPACE.test(v)) {
    return '密码不能包含空白字符';
  }
  if (!HAS_LETTER.test(v)) {
    return '密码需包含字母';
  }
  if (!HAS_DIGIT.test(v)) {
    return '密码需包含数字';
  }
  if (!HAS_SPECIAL.test(v)) {
    return '密码需包含特殊字符';
  }

  return null;
}
