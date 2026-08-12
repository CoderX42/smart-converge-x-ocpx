package com.yzytech.scx.ocpx.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzytech.scx.ocpx.admin.mapper.OcpxReportMatMapper;
import com.yzytech.scx.ocpx.admin.model.OcpxReportMat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Slf4j
@Service
public class OcpxReportMergeService extends ServiceImpl<OcpxReportMatMapper, OcpxReportMat> {

	@Transactional
	public void groupReportMinute(String groupTime, Integer mark) {
		List<OcpxReportMat> rows = getBaseMapper().groupReportMinute(groupTime, mark);
		if (CollectionUtils.isEmpty(rows)) {
			return;
		}
		log.info("groupReportMinute size:{}", rows.size());
		getBaseMapper().deleteReportMatTemp(groupTime, mark);
		saveBatch(rows);
	}

	@Transactional
	public void groupReport(String groupTime, int mark) {
		List<OcpxReportMat> rows = getBaseMapper().groupReport(groupTime, mark);
		if (!CollectionUtils.isEmpty(rows)) {
			remove(Wrappers.<OcpxReportMat>lambdaUpdate()
				.eq(OcpxReportMat::getMark, mark)
				.le(OcpxReportMat::getTimeHour, groupTime));
			saveBatch(rows);
		}
	}

}
