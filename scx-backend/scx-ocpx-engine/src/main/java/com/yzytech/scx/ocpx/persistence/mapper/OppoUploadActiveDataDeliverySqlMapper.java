package com.yzytech.scx.ocpx.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OppoUploadActiveDataDeliverySqlMapper {

	java.util.List<SelectFindAdvertiserRow> selectFindAdvertiser(@Param("linkId") String linkId);

	record SelectFindAdvertiserRow(String linkExt, String credentialConfigKey) {
	}

}
