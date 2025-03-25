package org.voidland.concurrent.queue;

import java.util.Optional;

/**
 * A multi-producer multi-consumer bounded and waiting portion queue.
 * <br><br>
 * This interface does not specify the type of synchronization of the queue.<br>
 * Null values are not allowed. Use {@link Optional} to bypass this restriction.<br>
 * <br>
 * Here is how to cease using an instance of the queue (if this is necessary at all) with no risk of a dead-lock:
 * <pre> {@code
 * MPMC_PortionQueue<E> queue = ...;
 * // Start as many consumer and producer threads as necessary; both sets may grow or shrink in time.
 * ...
 * // Make sure that the producers are done, i.e. join all producer threads.
 * queue.ensureAllPortionsAreRetrieved();	// Optional but may help.
 * // Make sure that all portions are consumed; the call above does not guarantee that. It may be acceptable to actively wait:
 * while (portionsConsumed < totalPortions);
 * // At this point, all consumer threads are very soon to be blocked in queue.retrievePortion().
 * // Since all processing ends for good, no more consumer threads are supposed to be started.
 * queue.stopConsumers(currentConsumerCount);
 * // Do not use the queue any more.
 * 
 * }</pre>  
 * 
 * @param <E> the type of the portions (elements) in the queue
 */
public interface MPMC_PortionQueue<E>
{
	/**
	 * Adds a portion to the queue, waiting for the queue to become non-full, if necessary.
	 * 
	 * @param portion the portion (element) to be added
	 * 
	 * @throws InterruptedException if the thread is interrupted while waiting for the queue to become non-full
	 * @throws NullPointerException if portion is null
	 */
	void addPortion(E portion)
			throws InterruptedException, NullPointerException;
	
	/**
	 * Retrieves a portion from the queue, waiting for the queue to become non-empty, if necessary.<br>
	 * Returns null if the queue's work is done. After a consumer receives a null portion it must never call this method again.
	 * 
	 * @return the retrieved portion (element); null if the queue is empty and no more portions will ever be added to it
	 * 
	 * @throws InterruptedException if the thread is interrupted while waiting for the queue to become non-empty
	 */
	E retrievePortion()
			throws InterruptedException;

	/**
	 * Waits until the queue becomes empty.<br>
	 * This method should be called only after all the producers are done adding portions to the queue,
	 * i.e. after the last call to {@link #addPortion(E)}.<br>
	 * Calling this method is not mandatory but it may help with fulfilling the requirements of {@link #stopConsumers(int)}.
	 * 
	 * @throws InterruptedException if the thread is interrupted while waiting for the portions to be retrieved
	 */
	void ensureAllPortionsAreRetrieved()
			throws InterruptedException;
	
	/**
	 * Makes sure that all consumers that have called or may call {@link #retrievePortion()} get a null portion instead of waiting.<br>
	 * After calling this method the queue must not be used anymore.<br>
	 * It is important that the consumer count is the final one, i.e. no more consumers using the queue will be ever be started.
	 * Do not get tempted to use a "safe" big number for the consumer count because the time and space complexity of this method may be O(finalConsumerThreadsCount).<br>
	 *  
	 *  @param finalConsumerThreadsCount the final consumer thread count; {@link #retrievePortion()} will return null at least that many times
	 *  
	 *  @throws InterruptedException if the thread is interrupted while stopping the consumers
	 */
	void stopConsumers(int finalConsumerThreadsCount)
		throws InterruptedException;
	
	/**
	 * Returns an approximation of the number of portions in the queue.<br>
	 * This method should be used only for logging.
	 * 
	 * @return the size of the queue at some moment shortly before the method returns
	 */
    int getSize();
    
    /**
     * Returns the targeted maximum size of the queue. 
     */
    int getMaxSize();
}
