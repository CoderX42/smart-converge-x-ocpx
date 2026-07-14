

package com.yzytech.scx.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.system.api.entity.SysSmsSendRecord;
import com.yzytech.scx.system.mapper.SysSmsSendRecordMapper;
import com.yzytech.scx.system.service.ISysSmsSendRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 短信发送记录
 *
 * @date 2025/7/03
 */
@Service
@RequiredArgsConstructor
public class SysSmsSendRecordServiceImpl extends ServiceImpl<SysSmsSendRecordMapper, SysSmsSendRecord>
		implements ISysSmsSendRecordService {

}
