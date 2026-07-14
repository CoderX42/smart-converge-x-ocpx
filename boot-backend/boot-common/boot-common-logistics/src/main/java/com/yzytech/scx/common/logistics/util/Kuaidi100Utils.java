

package com.yzytech.scx.common.logistics.util;

import cn.hutool.json.JSONUtil;
import com.google.gson.Gson;
import com.kuaidi100.sdk.api.QueryTrack;
import com.kuaidi100.sdk.api.Subscribe;
import com.kuaidi100.sdk.contant.ApiInfoConstant;
import com.kuaidi100.sdk.contant.CompanyConstant;
import com.kuaidi100.sdk.core.IBaseClient;
import com.kuaidi100.sdk.pojo.HttpResult;
import com.kuaidi100.sdk.request.*;
import com.kuaidi100.sdk.response.QueryTrackResp;
import com.kuaidi100.sdk.response.SubscribeResp;
import com.kuaidi100.sdk.utils.SignUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.context.annotation.Configuration;

/**
 * 快递100
 *
 * @date 2022/6/11
 */
@Slf4j
@Configuration
public class Kuaidi100Utils {

	/**
	 * 订阅接口
	 * @param logisticsCode 快递编码
	 * @param logisticsNo 快递单号
	 * @param address 收件地址
	 * @param key 快递100的key
	 * @param callbackUrl 回调地址
	 * @param telephone 手机号
	 * @param salt 签名用随机字符串
	 */
	public void poll(String logisticsCode, String logisticsNo, String address, String key, String callbackUrl,
			String telephone, String salt) {
		SubscribeParameters subscribeParameters = new SubscribeParameters();
		subscribeParameters.setCallbackurl(callbackUrl);
		subscribeParameters.setPhone(telephone);
		subscribeParameters.setSalt(salt);
		SubscribeParam subscribeParam = new SubscribeParam();
		subscribeParam.setParameters(subscribeParameters);
		subscribeParam.setCompany(logisticsCode);
		subscribeParam.setNumber(logisticsNo);
		subscribeParam.setTo(address);
		subscribeParam.setKey(key);
		SubscribeReq subscribeReq = new SubscribeReq();
		subscribeReq.setSchema(ApiInfoConstant.SUBSCRIBE_SCHEMA);

		subscribeReq.setParam(new Gson().toJson(subscribeParam));

		IBaseClient subscribe = new Subscribe();
		HttpResult result;
		try {
			result = subscribe.execute(subscribeReq);
		}
		catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
		if (result.getStatus() != HttpStatus.SC_OK) {
			log.error("快递订阅失败：status：{}；error：{}", result.getStatus(), result.getError());
			throw new RuntimeException(result.getError());
		}
		SubscribeResp subscribeResp = JSONUtil.toBean(result.getBody(), SubscribeResp.class);
		log.info("快递订阅：{}；", subscribeResp.toString());

	}

	/**
	 * 实时快递查询接口
	 * @param customer
	 * @param key
	 * @param logisticsCode
	 * @param logisticsNo
	 * @param phone
	 * @return
	 */
	public QueryTrackResp queryTrack(String customer, String key, String logisticsCode, String logisticsNo,
			String phone) {
		QueryTrackReq queryTrackReq = new QueryTrackReq();
		QueryTrackParam queryTrackParam = new QueryTrackParam();
		queryTrackParam.setCom(logisticsCode);
		queryTrackParam.setNum(logisticsNo);
		queryTrackParam.setPhone(phone);
		String param = new Gson().toJson(queryTrackParam);

		queryTrackReq.setParam(param);
		queryTrackReq.setCustomer(customer);
		queryTrackReq.setSign(SignUtils.querySign(param, key, customer));

		IBaseClient baseClient = new QueryTrack();
		HttpResult result = null;
		try {
			result = baseClient.execute(queryTrackReq);
		}
		catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
		if (result.getStatus() != HttpStatus.SC_OK) {
			log.error("实时快递查询失败：status：{}；error：{}", result.getStatus(), result.getError());
			throw new RuntimeException(result.getError());
		}
		QueryTrackResp queryTrackResp = JSONUtil.toBean(result.getBody(), QueryTrackResp.class);
		log.info("实时快递查询：{}；", queryTrackResp.toString());
		return queryTrackResp;
	}

}
