package com.yzytech.scx.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.tracking.entity.ClickDedupGuard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ClickDedupGuardMapper extends BaseMapper<ClickDedupGuard> {

	@Select("SELECT * FROM scx_click_dedup_guard WHERE guard_key=#{guardKey} FOR UPDATE")
	ClickDedupGuard selectForUpdate(@Param("guardKey") String guardKey);

}
