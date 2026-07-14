

package com.yzytech.scx.generator.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.generator.entity.GenTableColumn;
import com.yzytech.scx.generator.mapper.GenTableColumnMapper;
import com.yzytech.scx.generator.service.IGenTableColumnService;
import org.springframework.stereotype.Service;

@Service
public class GenTableColumnServiceImpl extends ServiceImpl<GenTableColumnMapper, GenTableColumn>
		implements IGenTableColumnService {

}
