package com.yzytech.scx.subject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.subject.entity.Advertiser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AdvertiserMapper extends BaseMapper<Advertiser> {

	List<Advertiser> findAuthorized(@Param("userId") String userId);

	Optional<Advertiser> findAuthorizedById(@Param("userId") String userId, @Param("advertiserId") String advertiserId);
}
