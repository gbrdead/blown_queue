#ifndef __VOIDLAND_MPMC_PORTION_QUEUE_HPP__
#define __VOIDLAND_MPMC_PORTION_QUEUE_HPP__

#include <cstddef>
#include <optional>


namespace voidland::concurrent::queue
{


template <typename E>
class MPMC_PortionQueue
{
public:
    virtual ~MPMC_PortionQueue();

    virtual void addPortion(const E& portion) = 0;
    virtual void addPortion(E&& portion) = 0;
    virtual std::optional<E> retrievePortion() = 0;

    // The following two methods must be called in succession after the producer threads are done adding portions.
    // After that, the queue cannot be reused.
    virtual void ensureAllPortionsAreRetrieved() = 0;
    virtual void stopConsumers(std::size_t finalConsumerCount) = 0;

    virtual std::size_t getSize() = 0;
    virtual std::size_t getMaxSize() = 0;
};

template <typename E>
MPMC_PortionQueue<E>::~MPMC_PortionQueue()
{
}


}	// namespace voidland::concurrent::queue

#endif
