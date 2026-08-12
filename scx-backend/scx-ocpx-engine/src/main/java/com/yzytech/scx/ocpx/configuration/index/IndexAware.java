package com.yzytech.scx.ocpx.configuration.index;

/**
 * Engine 配置内存索引的最小操作契约。
 *
 * @param <K> 索引键类型
 * @param <V> 配置实体类型
 */
public interface IndexAware<K, V> {

	/** 根据主键读取当前内存快照。 */
	V get(K key);

	/** 向当前索引写入一条配置。 */
	void add(K key, V value);

	/** 清空当前索引，通常用于全量刷新。 */
	void clear();

}
