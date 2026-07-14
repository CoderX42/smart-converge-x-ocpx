

package com.yzytech.scx.common.core.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GeoLocation {

	private String province;

	private String city;

	private String region;

}
