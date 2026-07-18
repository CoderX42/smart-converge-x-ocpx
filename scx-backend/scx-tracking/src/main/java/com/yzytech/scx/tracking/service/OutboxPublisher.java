package com.yzytech.scx.tracking.service;

import com.yzytech.scx.tracking.entity.EventOutbox;

/** Publishes a committed outbox record without changing its database state. */
public interface OutboxPublisher {

	void publish(EventOutbox event);

}
