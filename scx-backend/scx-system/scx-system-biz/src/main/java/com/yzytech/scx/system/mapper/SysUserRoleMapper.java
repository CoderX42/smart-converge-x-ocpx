

package com.yzytech.scx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.system.api.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户关联角色
 *
 * @since 2022/2/26 16:50
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

}
