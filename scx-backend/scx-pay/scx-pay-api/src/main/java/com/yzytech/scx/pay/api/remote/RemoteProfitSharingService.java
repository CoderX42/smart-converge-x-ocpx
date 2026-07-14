

package com.yzytech.scx.pay.api.remote;

import com.yzytech.scx.pay.api.dto.ProfitSharingBindReqDTO;
import com.yzytech.scx.pay.api.dto.ProfitSharingSettleReqDTO;
import com.yzytech.scx.pay.api.dto.ProfitSharingUnbindReqDTO;

/**
 */
public interface RemoteProfitSharingService {

	/**
	 * 绑定分账方
	 * @param profitSharingBindReqDTO
	 * @return
	 */
	boolean bind(ProfitSharingBindReqDTO profitSharingBindReqDTO);

	/**
	 * 解绑分账方
	 * @param profitSharingUnBindReqDTO
	 * @return
	 */
	boolean unbind(ProfitSharingUnbindReqDTO profitSharingUnBindReqDTO);

	boolean profitSharing(ProfitSharingSettleReqDTO profitSharingSettleReqDTO);

}
