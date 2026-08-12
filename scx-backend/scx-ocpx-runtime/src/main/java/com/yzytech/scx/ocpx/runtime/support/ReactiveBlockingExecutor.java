package com.yzytech.scx.ocpx.runtime.support;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;

/**
 * 把同步 JDBC 调用隔离到 Reactor 弹性线程池。
 *
 * <p>
 * SCX 当前使用 JdbcTemplate，它会阻塞当前线程。Controller、Service 和 Adapter
 * 可以继续使用清晰的 Reactor 调用链，但必须通过本类执行这些阻塞片段，不能占用 Netty 事件循环线程。
 * </p>
 */
@Component
public class ReactiveBlockingExecutor {

	/** 在 boundedElastic 上执行一个有返回值的同步操作。 */
	public <T> Mono<T> call(Callable<T> action) {
		return Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic());
	}

	/** 在 boundedElastic 上执行一个无返回值的同步操作。 */
	public Mono<Void> run(Runnable action) {
		return Mono.fromRunnable(action).subscribeOn(Schedulers.boundedElastic()).then();
	}

}
