package com.yzytech.scx.tracking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.tracking.entity.MachineNonce;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MachineNonceMapper extends BaseMapper<MachineNonce> {

	@Delete("DELETE FROM scx_machine_nonce WHERE expires_at < NOW(3) LIMIT 500")
	int deleteExpired();

}
