package com.yzytech.scx.ocpx.index;

public interface IndexAware<K, V> {

	V get(K key);

	void add(K key, V value);

	void clear();

}
