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
 * 
 * ...	// Active use of the queue.
 * 
 * queue.stopConsumers();
 * // Each consumer will get a null portion and should interpret this as a signal to stop using the queue. 
 * }</pre>
 * If you have an unrelated mechanism of terminating the consumers then calling {@link #stopConsumers()}
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
	 * Makes sure that all consumers that have called or may call {@link #retrievePortion()} get a {@code null} portion instead of waiting.
	 * <br>
	 * The portions that are already in the queue will be consumed first.
	 * After calling this method no new portions should be added to the queue.
	 *  
	 *  @throws InterruptedException if the current thread is interrupted while stopping the consumers
	 */
	void stopConsumers()
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
