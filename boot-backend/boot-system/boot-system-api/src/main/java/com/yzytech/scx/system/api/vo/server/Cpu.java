

package com.yzytech.scx.system.api.vo.server;

import cn.hutool.core.util.NumberUtil;

/**
 * CPU相关信息
 *
 * @since 2022/5/21 15:48
 */
public class Cpu {

	/** 核心数 */
	private int cpuNum;

	/** CPU总的使用率 */
	private double total;

	/** CPU系统使用率 */
	private double sys;

	/** CPU用户使用率 */
	private double used;

	/** CPU当前等待率 */
	private double wait;

	/** CPU当前空闲率 */
	private double free;

	public int getCpuNum() {

		return cpuNum;
	}

	public void setCpuNum(int cpuNum) {

		this.cpuNum = cpuNum;
	}

	public double getTotal() {
		return NumberUtil.round(NumberUtil.mul(total, 100), 2).doubleValue();
	}

	public double getSys() {
		return NumberUtil.round(NumberUtil.mul(sys / total, 100), 2).doubleValue();
	}

	public double getUsed() {
		return NumberUtil.round(NumberUtil.mul(used / total, 100), 2).doubleValue();
	}

	public double getWait() {
		return NumberUtil.round(NumberUtil.mul(wait / total, 100), 2).doubleValue();
	}

	public double getFree() {
		return NumberUtil.round(NumberUtil.mul(free / total, 100), 2).doubleValue();
	}

	public void setTotal(double total) {

		this.total = total;
	}

	public void setSys(double sys) {

		this.sys = sys;
	}

	public void setUsed(double used) {

		this.used = used;
	}

	public void setWait(double wait) {

		this.wait = wait;
	}

	public void setFree(double free) {

		this.free = free;
	}

}
