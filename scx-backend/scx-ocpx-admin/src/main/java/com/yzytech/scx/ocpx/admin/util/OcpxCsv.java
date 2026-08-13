package com.yzytech.scx.ocpx.admin.util;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 极简 CSV 内容拼装：首行表头，逐行逗号拼接，null 输出 0，不做字段转义。
 */
public final class OcpxCsv {

	private OcpxCsv() {
	}

	public static String content(List<String> header, List<List<Object>> rows) {
		StringBuilder total = new StringBuilder();
		total.append(String.join(",", header)).append("\n");
		for (List<Object> row : rows) {
			total.append(row.stream().map(value -> value == null ? "0" : String.valueOf(value)).collect(
					Collectors.joining(",")))
				.append("\n");
		}
		return total.toString();
	}

}
