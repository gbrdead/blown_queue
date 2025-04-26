#ifndef __VOIDLAND_NON_BLOCKING_QUEUE_HPP__
#define __VOIDLAND_NON_BLOCKING_QUEUE_HPP__

#include <cstddef>
#include <memory>
#include <cmath>

#include "blown_queue_config.h"

#ifdef HAVE_ATOMIC_QUEUE
#	include <atomic_queue/atomic_queue.h>
#endif

#ifdef HAVE_MOODYCAMEL_CONCURRENT_QUEUE
#	ifdef HAVE_MOODYCAMEL_IN_INCLUDE_PATH
#		include <concurrentqueue/moodycamel/concurrentqueue.h>
#	else
#		include <concurrentqueue/concurrentqueue.h>
#	endif
#endif

#ifdef HAVE_BOOST_LOCKFREE
#	include <boost/lockfree/queue.hpp>
#endif

#ifdef HAVE_XENIUM
#	include <xenium/reclamation/generic_epoch_based.hpp>
#	include <xenium/michael_scott_queue.hpp>
#	include <xenium/ramalhete_queue.hpp>
#	include <xenium/vyukov_bounded_queue.hpp>
#endif



namespace voidland::concurrent::queue
{


template <typename E>
class NonBlockingQueue
{
public:
	virtual ~NonBlockingQueue();

	virtual bool tryEnqueue(E&& portion) = 0;
	virtual bool tryDequeue(E& portion) = 0;
};

template <typename E>
NonBlockingQueue<E>::~NonBlockingQueue()
{
}



#ifdef HAVE_ATOMIC_QUEUE


template <typename E>
class AtomicPortionQueue :
    public NonBlockingQueue<E>
{
private:
    atomic_queue::AtomicQueueB2<E> queue;

public:
    AtomicPortionQueue(std::size_t maxSize);

    bool tryEnqueue(E&& portion);
    bool tryDequeue(E& portion);
};

template <typename E>
AtomicPortionQueue<E>::AtomicPortionQueue(std::size_t maxSize) :
	queue(maxSize)
{
}

template <typename E>
bool AtomicPortionQueue<E>::tryEnqueue(E&& portion)
{
    this->queue.push(std::move(portion));
    return true;
}

template <typename E>
bool AtomicPortionQueue<E>::tryDequeue(E& portion)
{
    return this->queue.try_pop(portion);
}


#endif	// HAVE_ATOMIC_QUEUE



#ifdef HAVE_MOODYCAMEL_CONCURRENT_QUEUE


template <typename E>
class ConcurrentPortionQueue :
    public NonBlockingQueue<E>
{
private:
    moodycamel::ConcurrentQueue<E> queue;

public:
    ConcurrentPortionQueue(std::size_t maxSize);

    bool tryEnqueue(E&& portion);
    bool tryDequeue(E& portion);
};

template <typename E>
ConcurrentPortionQueue<E>::ConcurrentPortionQueue(std::size_t maxSize) :
	queue(maxSize)
{
}

template <typename E>
bool ConcurrentPortionQueue<E>::tryEnqueue(E&& portion)
{
    return this->queue.enqueue(std::move(portion));
}

template <typename E>
bool ConcurrentPortionQueue<E>::tryDequeue(E& portion)
{
    return this->queue.try_dequeue(portion);
}


#endif	// HAVE_MOODYCAMEL_CONCURRENT_QUEUE



#ifdef HAVE_BOOST_LOCKFREE


template <typename E>
class LockfreePortionQueue :
    public NonBlockingQueue<E>
{
private:
    boost::lockfree::queue<E*> queue;

public:
    LockfreePortionQueue(std::size_t maxSize);
    ~LockfreePortionQueue();

    bool tryEnqueue(E&& portion);
    bool tryDequeue(E& portion);
};

template <typename E>
LockfreePortionQueue<E>::LockfreePortionQueue(std::size_t maxSize) :
	queue(maxSize)
{
}

template <typename E>
LockfreePortionQueue<E>::~LockfreePortionQueue()
{
    this->queue.consume_all(
        [](E* portionCopy)
        {
            delete portionCopy;
        });
}

template <typename E>
bool LockfreePortionQueue<E>::tryEnqueue(E&& portion)
{
    E* portionCopy = new E(std::move(portion));
    bool success = this->queue.bounded_push(portionCopy);
    if (!success)
    {
        portion = std::move(*portionCopy);
        delete portionCopy;
    }
    return success;
}

template <typename E>
bool LockfreePortionQueue<E>::tryDequeue(E& portion)
{
    E* portionCopy;
    bool success = this->queue.pop(portionCopy);
    if (success)
    {
        portion = std::move(*portionCopy);
        delete portionCopy;
    }
    return success;
}


#endif	// HAVE_BOOST_LOCKFREE



#ifdef HAVE_XENIUM


template <typename E>
class MichaelScottPortionQueue :
    public NonBlockingQueue<E>
{
private:
    xenium::michael_scott_queue<E, xenium::policy::reclaimer<xenium::reclamation::epoch_based<>>> queue;

public:
    MichaelScottPortionQueue(std::size_t maxSize);

    bool tryEnqueue(E&& portion);
    bool tryDequeue(E& portion);
};

template <typename E>
MichaelScottPortionQueue<E>::MichaelScottPortionQueue(std::size_t maxSize) :
	queue()
{
}

template <typename E>
bool MichaelScottPortionQueue<E>::tryEnqueue(E&& portion)
{
    this->queue.push(std::move(portion));
    return true;
}

template <typename E>
bool MichaelScottPortionQueue<E>::tryDequeue(E& portion)
{
    return this->queue.try_pop(portion);
}



template <typename E>
class RamalhetePortionQueue :
    public NonBlockingQueue<E>
{
private:
	xenium::ramalhete_queue<E*, xenium::policy::reclaimer<xenium::reclamation::epoch_based<>>> queue;

public:
    RamalhetePortionQueue(std::size_t maxSize);
    ~RamalhetePortionQueue();

    bool tryEnqueue(E&& portion);
    bool tryDequeue(E& portion);
};

template <typename E>
RamalhetePortionQueue<E>::RamalhetePortionQueue(std::size_t maxSize) :
	queue()
{
}

template <typename E>
RamalhetePortionQueue<E>::~RamalhetePortionQueue()
{
	E* portionCopy;
	while (this->queue.try_pop(portionCopy))
	{
	    delete portionCopy;
	}
}

template <typename E>
bool RamalhetePortionQueue<E>::tryEnqueue(E&& portion)
{
    E* portionCopy = new E(std::move(portion));
    this->queue.push(portionCopy);
    return true;
}

template <typename E>
bool RamalhetePortionQueue<E>::tryDequeue(E& portion)
{
    E* portionCopy;
    bool success = this->queue.try_pop(portionCopy);
    if (success)
    {
        portion = std::move(*portionCopy);
        delete portionCopy;
    }
    return success;
}



template <typename E>
class VyukovPortionQueue :
    public NonBlockingQueue<E>
{
private:
    xenium::vyukov_bounded_queue<E> queue;

public:
    VyukovPortionQueue(std::size_t maxSize);

    bool tryEnqueue(E&& portion);
    bool tryDequeue(E& portion);
};

template <typename E>
VyukovPortionQueue<E>::VyukovPortionQueue(std::size_t maxSize) :
	queue(1 << (unsigned)std::ceill(std::log2l(maxSize)))
{
}

template <typename E>
bool VyukovPortionQueue<E>::tryEnqueue(E&& portion)
{
    return this->queue.try_push(std::move(portion));
}

template <typename E>
bool VyukovPortionQueue<E>::tryDequeue(E& portion)
{
    return this->queue.try_pop(portion);
}


#endif // HAVE_XENIUM


}	// namespace voidland::concurrent::queue

#endif
