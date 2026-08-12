package com.yzytech.scx.ocpx.index;

import com.yzytech.scx.ocpx.dao.mapper.OcpxAdvertiserConfigMapper;
import com.yzytech.scx.ocpx.dao.mapper.OcpxMediaConfigMapper;
import com.yzytech.scx.ocpx.dao.mapper.OcpxMonitorTaskConfigMapper;
import com.yzytech.scx.ocpx.dao.mapper.OcpxPromotionLinkConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexDataLoader {

	private final OcpxAdvertiserConfigMapper advertiserMapper;

	private final OcpxPromotionLinkConfigMapper promotionLinkMapper;

	private final OcpxMediaConfigMapper mediaMapper;

	private final OcpxMonitorTaskConfigMapper monitorTaskMapper;

	private final UserDspIndex userDspIndex;

	private final DspLinkIndex dspLinkIndex;

	private final UserChannelIndex userChannelIndex;

	private final ChannelMonitorIndex channelMonitorIndex;

	public void init() {
		log.info("IndexDataLoader start");
		userDspIndex.clear();
		advertiserMapper.selectList(null).forEach(item -> userDspIndex.add(item.getId(), item));
		dspLinkIndex.clear();
		promotionLinkMapper.selectList(null).forEach(item -> dspLinkIndex.add(item.getId(), item));
		userChannelIndex.clear();
		mediaMapper.selectList(null).forEach(item -> userChannelIndex.add(item.getId(), item));
		channelMonitorIndex.clear();
		monitorTaskMapper.selectList(null).forEach(item -> channelMonitorIndex.add(item.getId(), item));
		log.info("IndexDataLoader end");
	}

}
