package com.yzytech.scx.creative.port;

public interface AdGroupScopePort {

	void requireReadable(String adGroupId);

	void requireWritable(String adGroupId);

}
