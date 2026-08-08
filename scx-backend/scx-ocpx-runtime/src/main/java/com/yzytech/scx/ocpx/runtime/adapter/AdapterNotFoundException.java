package com.yzytech.scx.ocpx.runtime.adapter;

/** 找不到已注册协议适配器时的明确异常，避免把 Adapter 自身的参数错误误判成“未注册”。 */
public class AdapterNotFoundException extends IllegalArgumentException {

	public AdapterNotFoundException(String direction, String code) {
		super("unknown " + direction.toLowerCase() + " adapter: " + code);
	}

}
