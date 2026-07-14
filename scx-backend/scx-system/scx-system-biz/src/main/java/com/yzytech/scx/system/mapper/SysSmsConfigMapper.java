

package com.yzytech.scx.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.system.api.entity.SysSmsConfig;
import com.yzytech.scx.system.api.vo.SysSmsConfigVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 短信配置
 *
 * @date 2025/7/02
 */
@Mapper
public interface SysSmsConfigMapper extends BaseMapper<SysSmsConfig> {

	SysSmsConfigVO getSmsConfigByCode(@Param("smsCode") String code);

	SysSmsConfigVO selectDefaultConfig();

}
