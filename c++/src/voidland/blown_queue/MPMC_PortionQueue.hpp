#ifndef __VOIDLAND_MPMC_PORTION_QUEUE_HPP__
#define __VOIDLAND_MPMC_PORTION_QUEUE_HPP__

#include <cstddef>
#include <optional>


/**
 * @file
 */

namespace voidland::concurrent::queue
{

/**
 * A multi-producer multi-consumer bounded and waiting portion queue.<br>
 * This interface does not specify the type of synchronization of the queue.<br>
 * <br>
 * General usage pattern:<br>
 * {@code
 * MPMC_PortionQueue<E>* queue = ...;
 *
 * ...	// Active use of the queue.
 *
 * queue->stopConsumers();
 * // Each consumer will get an empty portion and should interpret this as a signal to stop using the queue.
 * // Destroy queue.
 * }
 * <br><br>
 * If you have an unrelated mechanism of terminating the consumers then calling {@link MPMC_PortionQueue::stopConsumers()}
 * is not necessary. Just stop using the queue when it is empty.
 *
 * @param <E> the type of the portions (elements) in the queue
 */
template <typename E>
class MPMC_PortionQueue
{
public:
    virtual ~MPMC_PortionQueue();

	/**
	 * Adds a portion to the queue by copying it, waiting for the queue to become non-full if necessary.
	 *
	 * @param portion the portion (element) to be added
	 */
    virtual void addPortion(const E& portion) = 0;
	/**
	 * Adds a portion to the queue by moving it, waiting for the queue to become non-full if necessary.
	 *
	 * @param portion the portion (element) to be added
	 */
    virtual void addPortion(E&& portion) = 0;
	/**
	 * Retrieves a portion from the queue, waiting for the queue to become non-empty if necessary.
	 * Returns {@code std::nullopt} if the queue's work is done. After a consumer receives a {@code std::nullopt} portion it must never call this method again.
	 *
	 * @return the retrieved portion (element); {@code std::nullopt} if the queue is empty and no more portions will ever be added to it
	 */
    virtual std::optional<E> retrievePortion() = 0;

	/**
	 * Makes sure that all consumers that have called or may call {@link retrievePortion()} get a {@code std::nullopt} portion instead of waiting.
	 * The portions that are already in the queue will be consumed first.
	 * After calling this method no new portions should be added to the queue.
	 */
    virtual void stopConsumers() = 0;

	/**
	 * Returns an approximation of the number of portions in the queue.
	 * This method should be used only for logging.
	 *
	 * @return the size of the queue at some moment shortly before the method returns
	 */
    virtual std::size_t getSize() = 0;

    /**
     * Returns the targeted maximum size of the queue.
     * The queue may exceed this size occasionally by just a few elements.
     *
     * @return the maximum size of the queue
     */
    virtual std::size_t getMaxSize() = 0;
};

template <typename E>
MPMC_PortionQueue<E>::~MPMC_PortionQueue()
{
}


}	// namespace voidland::concurrent::queue

#endif
