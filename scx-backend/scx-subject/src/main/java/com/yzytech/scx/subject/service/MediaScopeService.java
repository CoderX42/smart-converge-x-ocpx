package com.yzytech.scx.subject.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yzytech.scx.common.core.constant.CommonConstants;
import com.yzytech.scx.common.security.entity.BootUser;
import com.yzytech.scx.common.security.util.SecurityUtils;
import com.yzytech.scx.subject.entity.UserSubjectScope;
import com.yzytech.scx.subject.mapper.UserSubjectScopeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaScopeService {
    private final UserSubjectScopeMapper scopeMapper;

    public boolean isAdmin() {
        BootUser user = SecurityUtils.getUser();
        return user != null && user.getRoles() != null && user.getRoles().contains(CommonConstants.ROLE_ADMIN_CODE);
    }

    public List<String> readableMediaIds() {
        if (isAdmin()) return Collections.emptyList();
        BootUser user = SecurityUtils.getUser();
        if (user == null) return List.of();
        return scopeMapper.selectList(Wrappers.<UserSubjectScope>lambdaQuery()
                .eq(UserSubjectScope::getUserId, user.getUserId())
                .eq(UserSubjectScope::getSubjectType, "MEDIA"))
            .stream().map(UserSubjectScope::getSubjectId).toList();
    }

    public boolean canRead(String mediaId) {
        return isAdmin() || readableMediaIds().contains(mediaId);
    }

    public boolean canWrite(String mediaId) {
        BootUser user = SecurityUtils.getUser();
        return user != null && (isAdmin() || (user.getRoles() != null && user.getRoles().contains("ROLE_OPERATOR") && canRead(mediaId)));
    }
}
