package com.yzytech.scx.ocpx.feedback.web.mi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MiReqTest {

	@Test
	void cleansUnreplacedMacrosToNull() {
		MiReq req = new MiReq();
		req.setRequest_id("__REQUEST_ID__");
		req.setCallback_url("__CALLBACK_URL__");
		req.setCaid("__CAID__");
		req.setCaid_list("__CAID_LIST__");
		req.setCaid1("__CAID1__");
		req.setLast_caid("__LAST_CAID__");
		req.setPrev_caid("__PREV_CAID__");
		req.setIdfa("__IDFA__");
		req.setIdfa_md5("__IDFA_MD5__");
		req.setAaid("__AAID__");
		req.setImei("__IMEI__");
		req.setOaid("__OAID__");
		req.setOaid_md5("__OAID_MD5__");
		req.setAndroidid("__ANDROIDID__");
		req.setMac("__MAC__");
		req.setIp("__IP__");
		req.setOs("__OS__");
		req.setTs("__TS__");
		req.setUa("__UA__");
		req.setSign("__SIGN__");
		req.setAdv_id("__ADVERTISER_ID__");
		req.setGroup_id("__ORDER_GROUP_ID__");
		req.setPlan_id("__ORDER_PLAN_ID__");
		req.setCreative_id("__CREATIVE_ID__");
		req.setImpresss_id("__IMPRESSS_ID__");
		req.setClickid("__CLICKID__");
		req.setCallback("__CALLBACK__");

		assertThat(req.getRequest_id()).isNull();
		assertThat(req.getCallback_url()).isNull();
		assertThat(req.getCaid()).isNull();
		assertThat(req.getCaid_list()).isNull();
		assertThat(req.getCaid1()).isNull();
		assertThat(req.getLast_caid()).isNull();
		assertThat(req.getPrev_caid()).isNull();
		assertThat(req.getIdfa()).isNull();
		assertThat(req.getIdfa_md5()).isNull();
		assertThat(req.getAaid()).isNull();
		assertThat(req.getImei()).isNull();
		assertThat(req.getOaid()).isNull();
		assertThat(req.getOaid_md5()).isNull();
		assertThat(req.getAndroidid()).isNull();
		assertThat(req.getMac()).isNull();
		assertThat(req.getIp()).isNull();
		assertThat(req.getOs()).isNull();
		assertThat(req.getTs()).isNull();
		assertThat(req.getUa()).isNull();
		assertThat(req.getSign()).isNull();
		assertThat(req.getAdv_id()).isNull();
		assertThat(req.getGroup_id()).isNull();
		assertThat(req.getPlan_id()).isNull();
		assertThat(req.getCreative_id()).isNull();
		assertThat(req.getImpresss_id()).isNull();
		assertThat(req.getClickid()).isNull();
		assertThat(req.getCallback()).isNull();
	}

	@Test
	void fallsBackToCurrentMillisWhenMtsMacroIsNotReplaced() {
		MiReq req = new MiReq();
		req.setMts("__MTS__");

		long before = System.currentTimeMillis();
		String tsMs = req.getTs_ms();
		long after = System.currentTimeMillis();

		assertThat(tsMs).matches("\\d+");
		assertThat(Long.parseLong(tsMs)).isBetween(before, after);
	}

	@Test
	void keepsFieldsWithoutMacroCleaningGettersUntouched() {
		MiReq req = new MiReq();
		req.setImei_md5("__IMEI_MD5__");
		req.setMac_md5("__MAC_MD5__");
		req.setMac1_md5("__MAC1_MD5__");
		req.setModel("__MODEL__");
		req.setOnce_encode_sua("__ONCE_ENCODE_SUA__");
		req.setMts("1700000000000");

		// SGYZ 未为这些字段提供清洗 getter，宏文本按原样透传。
		assertThat(req.getImei_md5()).isEqualTo("__IMEI_MD5__");
		assertThat(req.getMac_md5()).isEqualTo("__MAC_MD5__");
		assertThat(req.getMac1_md5()).isEqualTo("__MAC1_MD5__");
		assertThat(req.getModel()).isEqualTo("__MODEL__");
		assertThat(req.getOnce_encode_sua()).isEqualTo("__ONCE_ENCODE_SUA__");
		assertThat(req.getTs_ms()).isEqualTo("1700000000000");
	}

}
