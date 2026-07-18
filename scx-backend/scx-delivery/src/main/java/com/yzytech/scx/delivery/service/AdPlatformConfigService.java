package com.yzytech.scx.delivery.service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.delivery.entity.AdPlatformConfig;
import com.yzytech.scx.delivery.mapper.AdPlatformConfigMapper;
import com.yzytech.scx.subject.service.MediaScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
@Service @RequiredArgsConstructor public class AdPlatformConfigService {
 private final AdPlatformConfigMapper mapper; private final MediaScopeService scopeService;
 public Page<AdPlatformConfig> page(Page<AdPlatformConfig> page,String mediaId,String keyword){ requireRead(mediaId); return mapper.selectPage(page,Wrappers.<AdPlatformConfig>lambdaQuery().eq(AdPlatformConfig::getMediaId,mediaId).and(StringUtils.hasText(keyword),q->q.like(AdPlatformConfig::getConfigKey,keyword).or().like(AdPlatformConfig::getDescription,keyword)).orderByAsc(AdPlatformConfig::getConfigKey)); }
 @Transactional public boolean save(AdPlatformConfig value){ requireWrite(value.getMediaId()); if(value.getId()!=null){ AdPlatformConfig old=mapper.selectById(value.getId()); if(old==null||!old.getMediaId().equals(value.getMediaId()))throw new IllegalArgumentException("资源不存在"); } if(value.getStatus()==null)value.setStatus("ACTIVE"); if(value.getVersion()==null)value.setVersion(0); return value.getId()==null?mapper.insert(value)==1:mapper.updateById(value)==1; }
 private void requireRead(String id){if(id==null||!scopeService.canRead(id))throw new IllegalArgumentException("资源不存在");} private void requireWrite(String id){if(id==null||!scopeService.canWrite(id))throw new IllegalArgumentException("资源不存在");}
}
