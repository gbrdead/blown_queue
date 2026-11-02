package org.voidland.concurrent.queue;

import java.util.Optional;

/**
 * A multi-producer multi-consumer bounded and waiting portion queue.
 * <br>
 * This interface does not specify the type of synchronization of the queue.<br>
 * Null values are not allowed. Use {@link Optional} to bypass this restriction.<br>
 * <br>
 * General usage pattern:<br>
 * <pre> {@code
 * MPMC_PortionQueue<E> queue = ...;
 * AtomicInteger currentConsumerCount = new AtomicInteger(0);
 * // Start as many consumer and producer threads as necessary.
 * // Both sets may grow or shrink over time as a result of production and consumption:
 * // - A new consumer/producer thread may be started by another consumer/producer thread.
 * // - A consumer/producer thread may decide to terminate.
 * // Keep the current consumer count in currentConsumerCount.
 * 
 * ...	// Active use of the queue.
 * 
 * // Here is how to stop using the queue without any risk of a deadlock:
 * 
 * // First, make sure that the producers are done with using the queue.
 * // Then call:
 * queue.ensureAllPortionsAreRetrieved();	// Optional but will help to minimize the active waiting below.
 * 
 * // Now you need to settle the final consumer count.
 * // If a consumer can never be started by another consumer then you can skip directly to calling queue.stopConsumers() below.
 * 
 * // Make sure that all portions are consumed; queue.ensureAllPortionsAreRetrieved() does not guarantee that.
 * // It may be acceptable to actively wait, e.g.:
 * while (portionsConsumed < totalPortions);	// portionsConsumed and totalPortions most likely need to be atomic.
 * // At this point, all consumer threads are bound to become blocked in queue.retrievePortion().
 * // None of them will start new consumer threads or exit on its own.
 * 
 * // Now that the current consumer count is guaranteed to be the final one, call:
 * queue.stopConsumers(currentConsumerCount.get());
 * // Each consumer will get a null portion and interpret this as a signal to stop using the queue. 
 * }</pre>
 * If the number of consumers is fixed or a new consumer is never started as a result of consumption
 * then just a call to {@link #stopConsumers(int)} after making sure that the producers are done is enough.
 * <br>
 * If you have an unrelated mechanism of terminating the consumers then calling {@link #stopConsumers(int)}
 * is not necessary. Just stop using the queue when it is empty.
 * 
 * @param <E> the type of the portions (elements) in the queue
 */
public interface MPMC_PortionQueue<E>
{
	/**
	 * Adds a portion to the queue, waiting for the queue to become non-full if necessary.
	 * 
	 * @param portion the portion (element) to be added
	 * 
	 * @throws InterruptedException if the current thread is interrupted while waiting for the queue to become non-full
	 * @throws NullPointerException if {@code portion} is {@code null}
	 */
	void addPortion(E portion)
			throws InterruptedException, NullPointerException;
	
	/**
	 * Retrieves a portion from the queue, waiting for the queue to become non-empty if necessary.
	 * <br>
	 * Returns {@code null} if the queue's work is done. After a consumer receives a {@code null} portion it must never call this method again.
	 * 
	 * @return the retrieved portion (element); {@code null} if the queue is empty and no more portions will ever be added to it
	 * 
	 * @throws InterruptedException if the current thread is interrupted while waiting for the queue to become non-empty
	 */
	E retrievePortion()
			throws InterruptedException;

	/**
	 * Waits until the queue becomes empty.
	 * <br>
	 * This method should be called only after all the producers are done adding portions to the queue,
	 * i.e. after the last call to {@link #addPortion(E)}.<br>
	 * Calling this method is not mandatory but it may help with fulfilling the requirements of {@link #stopConsumers(int)}.
	 * 
	 * @throws InterruptedException if the current thread is interrupted while waiting for the portions to be retrieved
	 */
	void ensureAllPortionsAreRetrieved()
			throws InterruptedException;
	
	/**
	 * Makes sure that all consumers that have called or may call {@link #retrievePortion()} get a {@code null} portion instead of waiting.
	 * <br>
	 * After calling this method the queue must not be used anymore.<br>
	 * It is important that the consumer count is the final one, i.e. no more consumers using the queue will be ever be started.
	 * Do not be tempted to use a "safe" big number for the consumer count because the time and space complexity of this method may be O(finalConsumerCount).
	 *  
	 *  @param finalConsumerCount the final consumer count; {@link #retrievePortion()} will return {@code null} at least that many times
	 *  
	 *  @throws InterruptedException if the current thread is interrupted while stopping the consumers
	 */
	void stopConsumers(int finalConsumerCount)
		throws InterruptedException;
	
	/**
	 * Returns an approximation of the number of portions in the queue.
	 * This method should be used only for logging.
	 * 
	 * @return the size of the queue at some moment shortly before the method returns
	 */
    int getSize();
    
    /**
     * Returns the targeted maximum size of the queue.
     * The queue may exceed this size occasionally by just a few elements. 
     * 
     * @return the maximum size of the queue 
     */
    int getMaxSize();
}
