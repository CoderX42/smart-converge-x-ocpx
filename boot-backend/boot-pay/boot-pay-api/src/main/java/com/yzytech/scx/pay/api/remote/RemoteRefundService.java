

package com.yzytech.scx.pay.api.remote;

import com.yzytech.scx.pay.api.dto.CreateRefundsReqDTO;

/**
 */
public interface RemoteRefundService {

	Object refunds(CreateRefundsReqDTO createRefundsReqDTO);

}
