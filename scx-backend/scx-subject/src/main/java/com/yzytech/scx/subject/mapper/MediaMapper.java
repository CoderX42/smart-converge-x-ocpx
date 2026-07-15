package com.yzytech.scx.subject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzytech.scx.subject.entity.Media;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface MediaMapper extends BaseMapper<Media> {

	List<Media> findAuthorized(@Param("userId") String userId);

	Optional<Media> findAuthorizedById(@Param("userId") String userId, @Param("mediaId") String mediaId);
}
