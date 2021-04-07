package reactor.core.publisher;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.LLLLLL_Result;
import org.openjdk.jcstress.infra.results.LLLLL_Result;
import org.openjdk.jcstress.infra.results.LLLL_Result;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;

public abstract class FluxSwitchOnFirstStressTest {

	final StressSubscription<String> inboundSubscription = new StressSubscription<>();
	final StressSubscriber<String>   inboundSubscriber   = new StressSubscriber<>(0);

	final StressSubscription<String> outboundSubscription = new StressSubscription<>();
	final StressSubscriber<String>   outboundSubscriber   = new StressSubscriber<>(0);

	final FluxSwitchOnFirst.SwitchOnFirstMain<String, String> main =
			new FluxSwitchOnFirst.SwitchOnFirstMain<>(outboundSubscriber,
					(fs, stream) -> new Flux<String>() {
						@Override
						public void subscribe(CoreSubscriber<? super String> actual) {
							stream.subscribe(inboundSubscriber);
							inboundSubscriber.request(1);
							outboundSubscription.subscribe(actual);
						}
					},
					false);

	{
		inboundSubscription.subscribe(main);
	}

	@JCStressTest
	@Outcome(id = {"1, 1, 1, 1, 1"}, expect = ACCEPTABLE)
	@State
	public static class OutboundOnSubscribeAndRequestStressTest
			extends FluxSwitchOnFirstStressTest {

		@Actor
		public void next() {
			main.onNext("test");
		}

		@Actor
		public void request() {
			outboundSubscription.request(1);
		}

		@Arbiter
		public void arbiter(LLLLL_Result result) {
			result.r1 = outboundSubscription.requestsCount;
			result.r2 = outboundSubscription.requested;
			result.r3 = inboundSubscription.requestsCount;
			result.r4 = inboundSubscription.requested;
			result.r5 = inboundSubscriber.onNextCalls;
		}
	}

	static class StressSubscription<T> implements Subscription {

		CoreSubscriber<? super T> actual;

		public volatile int subscribes;

		@SuppressWarnings("rawtypes")
		static final AtomicIntegerFieldUpdater<StressSubscription> SUBSCRIBES =
				AtomicIntegerFieldUpdater.newUpdater(StressSubscription.class,
						"subscribes");

		public volatile long requested;

		@SuppressWarnings("rawtypes")
		static final AtomicLongFieldUpdater<StressSubscription> REQUESTED =
				AtomicLongFieldUpdater.newUpdater(StressSubscription.class, "requested");

		public volatile int requestsCount;

		@SuppressWarnings("rawtypes")
		static final AtomicIntegerFieldUpdater<StressSubscription> REQUESTS_COUNT =
				AtomicIntegerFieldUpdater.newUpdater(StressSubscription.class,
						"requestsCount");

		public volatile boolean cancelled;

		void subscribe(CoreSubscriber<? super T> actual) {
			this.actual = actual;
			actual.onSubscribe(this);
			SUBSCRIBES.getAndIncrement(this);
		}

		@Override
		public void request(long n) {
			REQUESTS_COUNT.incrementAndGet(this);
			Operators.addCap(REQUESTED, this, n);
		}

		@Override
		public void cancel() {
			cancelled = true;
		}
	}
}
