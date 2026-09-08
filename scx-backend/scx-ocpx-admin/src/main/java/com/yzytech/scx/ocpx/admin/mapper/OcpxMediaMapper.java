package com.yzytech.scx.ocpx.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.ocpx.admin.dto.ManagementRows;
import com.yzytech.scx.ocpx.entity.OcpxMedia;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OcpxMediaMapper extends BaseMapper<OcpxMedia> {

	int countReferences(@Param("id") String id);

	int deleteRtaAccess(@Param("id") String id);

	List<ManagementRows.NamedOption> selectOptionsForAdvertiser(@Param("advertiserId") String advertiserId);

}
