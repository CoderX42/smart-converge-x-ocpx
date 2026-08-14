package com.yzytech.scx.system.api.util;

/**
 * 系统登录密码强度规则。
 */
public final class SystemPasswordPolicy {

	public static final int MIN_LENGTH = 6;

	public static final int MAX_LENGTH = 32;

	private SystemPasswordPolicy() {
	}

	/**
	 * 校验密码长度及字符组成。
	 * @param rawPassword 原始密码
	 * @throws IllegalArgumentException 密码不满足规则
	 */
	public static void validate(String rawPassword) {
		if (rawPassword == null || rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("密码长度必须为6-32位");
		}
		boolean hasLetter = false;
		boolean hasDigit = false;
		boolean hasSpecial = false;
		for (int offset = 0; offset < rawPassword.length();) {
			int codePoint = rawPassword.codePointAt(offset);
			if (Character.isWhitespace(codePoint)) {
				throw new IllegalArgumentException("密码不能包含空白字符");
			}
			if (Character.isLetter(codePoint)) {
				hasLetter = true;
			}
			else if (Character.isDigit(codePoint)) {
				hasDigit = true;
			}
			else {
				hasSpecial = true;
			}
			offset += Character.charCount(codePoint);
		}
		if (!hasLetter || !hasDigit || !hasSpecial) {
			throw new IllegalArgumentException("密码必须同时包含字母、数字和特殊字符");
		}
	}

}
