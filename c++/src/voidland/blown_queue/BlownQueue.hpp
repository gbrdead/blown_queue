#ifndef __VOIDLAND_MOSTLY_NON_BLOCKING_PORTION_QUEUE_HPP__
#define __VOIDLAND_MOSTLY_NON_BLOCKING_PORTION_QUEUE_HPP__

#include "NonBlockingQueue.hpp"
#include "MPMC_PortionQueue.hpp"

#include <atomic>
#include <mutex>
#include <condition_variable>
#include <utility>
#include <memory>
#include <new>
#include <cmath>

#include "blown_queue_config.h"



namespace voidland::concurrent::queue
{


#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Winterference-size"

template <typename E>
class BlownQueue :
    public MPMC_PortionQueue<E>
{
private:
	alignas(std::hardware_destructive_interference_size) std::atomic<std::size_t> size;
	std::size_t maxSize;

	alignas(std::hardware_destructive_interference_size) std::mutex mutex;
	alignas(std::hardware_destructive_interference_size) std::condition_variable notFullCondition;
	alignas(std::hardware_destructive_interference_size) std::condition_variable notEmptyCondition;
	alignas(std::hardware_destructive_interference_size) std::condition_variable emptyCondition;

    alignas(std::hardware_destructive_interference_size) std::atomic<bool> aProducerIsWaiting;
    alignas(std::hardware_destructive_interference_size) std::atomic<bool> aConsumerIsWaiting;

    // Keep the wrapped queue as a separately allocated object, otherwise the performance will suffer.
	alignas(std::hardware_destructive_interference_size) std::unique_ptr<NonBlockingQueue<E>> nonBlockingQueue;
    bool workDone;

public:
    BlownQueue(std::size_t maxSize, std::unique_ptr<NonBlockingQueue<E>> nonBlockingQueue);

    void addPortion(const E& portion);
    void addPortion(E&& portion);
    std::optional<E> retrievePortion();
    void ensureAllPortionsAreRetrieved();
    void stopConsumers(std::size_t finalConsumerCount);
    std::size_t getSize();
    std::size_t getMaxSize();

private:
    void lockMutexIfNecessary(std::unique_ptr<std::unique_lock<std::mutex>>& lock);
    void notifyAllWaitingProducers(std::unique_ptr<std::unique_lock<std::mutex>>& lock);
    void notifyAllWaitingConsumers(std::unique_ptr<std::unique_lock<std::mutex>>& lock);
};

#pragma GCC diagnostic pop


template <typename E>
BlownQueue<E>::BlownQueue(std::size_t maxSize, std::unique_ptr<NonBlockingQueue<E>> nonBlockingQueue) :
	size(0),
    maxSize(maxSize),
    mutex(),
	notFullCondition(),
	notEmptyCondition(),
    emptyCondition(),
	aProducerIsWaiting(false),
	aConsumerIsWaiting(false),
	nonBlockingQueue(std::move(nonBlockingQueue)),
    workDone(false)
{
}

template <typename E>
inline void BlownQueue<E>::lockMutexIfNecessary(std::unique_ptr<std::unique_lock<std::mutex>>& lock)
{
	if (!lock)
	{
		lock = std::make_unique<std::unique_lock<std::mutex>>(this->mutex);
	}
}

template <typename E>
inline void BlownQueue<E>::notifyAllWaitingProducers(std::unique_ptr<std::unique_lock<std::mutex>>& lock)
{
    bool expected = true;
    if (this->aProducerIsWaiting.compare_exchange_strong(expected, false, std::memory_order_acq_rel, std::memory_order_relaxed))
    {
		this->lockMutexIfNecessary(lock);
		this->notFullCondition.notify_all();
    }
}

template <typename E>
inline void BlownQueue<E>::notifyAllWaitingConsumers(std::unique_ptr<std::unique_lock<std::mutex>>& lock)
{
    bool expected = true;
    if (this->aConsumerIsWaiting.compare_exchange_strong(expected, false, std::memory_order_acq_rel, std::memory_order_relaxed))
    {
		this->lockMutexIfNecessary(lock);
		this->notEmptyCondition.notify_all();
    }
}


template <typename E>
void BlownQueue<E>::addPortion(const E& portion)
{
    E portionCopy(portion);
    this->addPortion(std::move(portionCopy));
}

template <typename E>
void BlownQueue<E>::addPortion(E&& portion)
{
	std::unique_ptr<std::unique_lock<std::mutex>> lock;

    while (true)
    {
        if (this->size.load(std::memory_order_acquire) >= this->maxSize)
        {
        	this->lockMutexIfNecessary(lock);

            while (true)
            {
            	bool expectedProducerIsWaiting = false;
            	bool theOnlyWaitingProducer = this->aProducerIsWaiting.compare_exchange_strong(expectedProducerIsWaiting, true, std::memory_order_acq_rel, std::memory_order_relaxed);

            	if (this->size.load(std::memory_order_acquire) < this->maxSize)
            	{
            		if (theOnlyWaitingProducer)
            		{
            			this->aProducerIsWaiting.store(false, std::memory_order_release);
            		}
            		break;
            	}

                this->notFullCondition.wait(*lock);
            }
        }

        if (this->nonBlockingQueue->tryEnqueue(std::move(portion)))
        {
        	break;
        }
    }
    this->size.fetch_add(1, std::memory_order_release);

    this->notifyAllWaitingConsumers(lock);
}

template <typename E>
std::optional<E> BlownQueue<E>::retrievePortion()
{
	std::unique_ptr<std::unique_lock<std::mutex>> lock;

    E portion;
    if (!this->nonBlockingQueue->tryDequeue(portion))
    {
    	this->lockMutexIfNecessary(lock);

        while (true)
        {
        	bool expectedConsumerIsWaiting = false;
        	bool theOnlyWaitingConsumer = this->aConsumerIsWaiting.compare_exchange_strong(expectedConsumerIsWaiting, true, std::memory_order_acq_rel, std::memory_order_relaxed);

        	if (this->nonBlockingQueue->tryDequeue(portion))
        	{
        		if (theOnlyWaitingConsumer)
        		{
        			this->aConsumerIsWaiting.store(false, std::memory_order_release);
        		}
        		break;
        	}

            if (this->workDone)
            {
                return std::nullopt;
            }

        	this->notEmptyCondition.wait(*lock);
        }
    }
    std::size_t newSize = this->size.fetch_sub(1, std::memory_order_acq_rel) - 1;

    this->notifyAllWaitingProducers(lock);

    if (newSize == 0)
    {
    	this->lockMutexIfNecessary(lock);
        this->emptyCondition.notify_one();
    }

    return portion;
}

template <typename E>
void BlownQueue<E>::ensureAllPortionsAreRetrieved()
{
	std::unique_lock lock(this->mutex);
	this->notEmptyCondition.notify_all();
	while (this->size.load(std::memory_order_acquire) > 0)
	{
		this->emptyCondition.wait(lock);
	}
}

template <typename E>
void BlownQueue<E>::stopConsumers(std::size_t finalConsumerCount)
{
	std::lock_guard lock(this->mutex);
	this->workDone = true;
	this->notEmptyCondition.notify_all();
}

template <typename E>
std::size_t BlownQueue<E>::getSize()
{
    return this->size.load(std::memory_order_relaxed);
}

template <typename E>
std::size_t BlownQueue<E>::getMaxSize()
{
    return this->maxSize;
}



#ifdef HAVE_ATOMIC_QUEUE

template <typename E>
inline std::unique_ptr<BlownQueue<E>> createAtomicBlownQueue(std::size_t maxSize)
{
    std::unique_ptr<queue::NonBlockingQueue<E>> nonBlockingQueue = std::make_unique<AtomicPortionQueue<E>>(maxSize);
	return std::make_unique<BlownQueue<E>>(maxSize, std::move(nonBlockingQueue));
}

#endif


#ifdef HAVE_MOODYCAMEL_CONCURRENT_QUEUE

template <typename E>
inline std::unique_ptr<BlownQueue<E>> createConcurrentBlownQueue(std::size_t maxSize)
{
    std::unique_ptr<queue::NonBlockingQueue<E>> nonBlockingQueue = std::make_unique<ConcurrentPortionQueue<E>>(maxSize);
	return std::make_unique<BlownQueue<E>>(maxSize, std::move(nonBlockingQueue));
}

#endif


#ifdef HAVE_BOOST_LOCKFREE

template <typename E>
inline std::unique_ptr<BlownQueue<E>> createLockfreeBlownQueue(std::size_t maxSize)
{
    std::unique_ptr<queue::NonBlockingQueue<E>> nonBlockingQueue = std::make_unique<LockfreePortionQueue<E>>(maxSize);
	return std::make_unique<BlownQueue<E>>(maxSize, std::move(nonBlockingQueue));
}

#endif


#ifdef HAVE_XENIUM

template <typename E>
inline std::unique_ptr<BlownQueue<E>> createMichaelScottBlownQueue(std::size_t maxSize)
{
    std::unique_ptr<queue::NonBlockingQueue<E>> nonBlockingQueue = std::make_unique<MichaelScottPortionQueue<E>>(maxSize);
	return std::make_unique<BlownQueue<E>>(maxSize, std::move(nonBlockingQueue));
}

template <typename E>
inline std::unique_ptr<BlownQueue<E>> createRamalheteBlownQueue(std::size_t maxSize)
{
    std::unique_ptr<queue::NonBlockingQueue<E>> nonBlockingQueue = std::make_unique<RamalhetePortionQueue<E>>(maxSize);
	return std::make_unique<BlownQueue<E>>(maxSize, std::move(nonBlockingQueue));
}

template <typename E>
inline std::unique_ptr<BlownQueue<E>> createVyukovBlownQueue(std::size_t maxSize)
{
	maxSize = 1 << (unsigned)std::floor(std::log2((long double)maxSize));
    std::unique_ptr<queue::NonBlockingQueue<E>> nonBlockingQueue = std::make_unique<VyukovPortionQueue<E>>(maxSize);
	return std::make_unique<BlownQueue<E>>(maxSize, std::move(nonBlockingQueue));
}

#endif


}	// namespace voidland::concurrent::queue

#endif
