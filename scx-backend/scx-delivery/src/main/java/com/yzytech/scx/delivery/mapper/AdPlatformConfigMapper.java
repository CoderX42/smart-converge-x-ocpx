package com.yzytech.scx.delivery.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.delivery.entity.AdPlatformConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdPlatformConfigMapper extends BaseMapper<AdPlatformConfig> {
    @Select("SELECT config_value FROM scx_ad_platform_config WHERE media_id=#{mediaId} AND config_key=#{configKey} AND status='ACTIVE' LIMIT 1")
    String selectActiveValue(@Param("mediaId") String mediaId, @Param("configKey") String configKey);
}
