

package com.yzytech.scx.common.idempotent.annotation;

import java.lang.annotation.*;

/**
 * 幂等注解
 *
 * @since 2025/4/30
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

	String key() default ""; // SpEL 表达式

	long expire() default 1; // 有效期（秒）

	String message() default "请勿重复提交"; // 自定义提示信息

	boolean delKey() default false;

}
