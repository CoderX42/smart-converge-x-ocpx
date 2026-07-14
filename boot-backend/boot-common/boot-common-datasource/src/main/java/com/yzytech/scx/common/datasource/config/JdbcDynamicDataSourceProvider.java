
package com.yzytech.scx.common.datasource.config;

import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import com.baomidou.dynamic.datasource.creator.druid.DruidConfig;
import com.baomidou.dynamic.datasource.provider.AbstractJdbcDataSourceProvider;
import com.baomidou.dynamic.datasource.toolkit.CryptoUtils;
import lombok.SneakyThrows;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class JdbcDynamicDataSourceProvider extends AbstractJdbcDataSourceProvider {

	public JdbcDynamicDataSourceProvider(DefaultDataSourceCreator dataSourceCreator,
			BootDataSourceProperties ysDataSourceProperties) {
		super(dataSourceCreator, ysDataSourceProperties.getDriverClassName(), ysDataSourceProperties.getUrl(),
				ysDataSourceProperties.getUsername(), ysDataSourceProperties.getPassword());
	}

	@SneakyThrows
	@Override
	protected Map<String, DataSourceProperty> executeStmt(Statement statement) {
		Map<String, DataSourceProperty> map = new HashMap<>();

		ResultSet rs = statement.executeQuery("select * from gen_data_source where del_flag = '0'");
		while (rs.next()) {
			String name = rs.getString("db_name");
			String username = rs.getString("username");
			String password = rs.getString("password");
			String url = rs.getString("url");
			DataSourceProperty property = new DataSourceProperty();
			property.setUsername(username);
			property.setPassword(CryptoUtils.decrypt(password));
			property.setUrl(url);
			DruidConfig druidConfig = new DruidConfig();
			druidConfig.setValidationQuery("select 1");
			property.setDruid(druidConfig);
			map.put(name, property);
		}
		return map;
	}

}
