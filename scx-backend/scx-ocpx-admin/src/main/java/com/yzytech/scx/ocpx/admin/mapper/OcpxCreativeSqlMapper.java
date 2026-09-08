package com.yzytech.scx.ocpx.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OcpxCreativeSqlMapper {

	int deleteDeleteCreativePromotionLink(@Param("id") String id);

	java.util.List<String> selectReplaceBindingsCreativePromotionLink(@Param("id") String id);

	java.util.List<SelectBindingOptionsCreativePromotionLinkRow> selectBindingOptionsCreativePromotionLink(
			@Param("creativeId") String creativeId);

	String selectLockCreativeCreative(@Param("id") String id);

	record SelectBindingOptionsCreativePromotionLinkRow(String id, String linkName) {
	}

	int deleteBindings(@Param("creativeId") String creativeId, @Param("ids") java.util.Set<String> ids);

	int insertBindings(@Param("advertiserId") String advertiserId, @Param("creativeId") String creativeId,
			@Param("ids") java.util.List<String> ids, @Param("operator") String operator);

	java.util.List<String> selectIdsByLink(@Param("linkId") String linkId, @Param("advertiserId") String advertiserId);

	java.util.List<com.yzytech.scx.system.api.entity.SysMaterial> selectAssets(@Param("owner") String owner,
			@Param("keyword") String keyword);

}
