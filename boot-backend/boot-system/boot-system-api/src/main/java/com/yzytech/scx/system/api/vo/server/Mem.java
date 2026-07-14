

package com.yzytech.scx.system.api.vo.server;

import cn.hutool.core.util.NumberUtil;

/**
 * 內存相关信息
 *
 * @since 2022/5/21 15:42
 */
public class Mem {

	/** 内存总量 */
	private double total;

	/** 已用内存 */
	private double used;

	/** 剩余内存 */
	private double free;

	public void setTotal(long total) {

		this.total = total;
	}

	public double getTotal() {
		return NumberUtil.div(total, (1024 * 1024 * 1024), 2);
	}

	public double getUsed() {
		return NumberUtil.div(used, (1024 * 1024 * 1024), 2);
	}

	public double getFree() {
		return NumberUtil.div(free, (1024 * 1024 * 1024), 2);
	}

	public double getUsage() {
		return NumberUtil.mul(NumberUtil.div(used, total, 4), 100);
	}

	public void setUsed(long used) {

		this.used = used;
	}

	public void setFree(long free) {

		this.free = free;
	}

}
