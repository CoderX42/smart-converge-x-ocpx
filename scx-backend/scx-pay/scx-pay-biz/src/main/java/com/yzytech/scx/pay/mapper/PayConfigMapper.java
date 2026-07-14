

package com.yzytech.scx.pay.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.pay.api.entity.PayConfig;
import com.yzytech.scx.pay.api.vo.PayConfigVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 支付配置
 *
 * @date 2022/6/16
 */
@Mapper
public interface PayConfigMapper extends BaseMapper<PayConfig> {

	/**
	 * 通过appid查询支付配置
	 * @param appId
	 * @return
	 */
	PayConfig selectByAppId(@Param("appId") String appId);

	/**
	 * 通过支付类型查询支付配置
	 * @param payType 支付类型
	 * @param terminalType 终端类型
	 * @return
	 */
	PayConfigVO selectConfig(@Param("payType") String payType, @Param("terminalType") String terminalType);

}
