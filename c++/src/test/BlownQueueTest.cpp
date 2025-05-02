#include "voidland/blown_queue/BlownQueue.hpp"

#include <gtest/gtest.h>
#include <memory>
#include <random>
#include <ctime>
#include <climits>
#include <unordered_set>
#include <mutex>
#include <vector>
#include <thread>
#include <latch>
#include <condition_variable>


using namespace voidland::concurrent::queue;


namespace test::voidland::concurrent::queue
{


template <typename E>
class BlownQueueCreator
{
public:
	virtual ~BlownQueueCreator()
	{
	}

	virtual std::unique_ptr<BlownQueue<E>> createBlownQueue(std::size_t maxSize) = 0;

	virtual std::unique_ptr<BlownQueue<E>> createBlownQueueWithMinimalSize()
	{
		return this->createBlownQueue(1);
	}
};


#ifdef HAVE_ATOMIC_QUEUE

template <typename E>
class AtomicBlownQueueCreator :
		public BlownQueueCreator<E>
{
public:
	std::unique_ptr<BlownQueue<E>> createBlownQueue(std::size_t maxSize) override
	{
		return createAtomicBlownQueue<E>(maxSize);
	}
};

#endif


#ifdef HAVE_MOODYCAMEL_CONCURRENT_QUEUE

template <typename E>
class ConcurrentBlownQueueCreator :
		public BlownQueueCreator<E>
{
public:
	std::unique_ptr<BlownQueue<E>> createBlownQueue(std::size_t maxSize) override
	{
		return createConcurrentBlownQueue<E>(maxSize);
	}
};

#endif


#ifdef HAVE_BOOST_LOCKFREE

template <typename E>
class LockfreeBlownQueueCreator :
		public BlownQueueCreator<E>
{
public:
	std::unique_ptr<BlownQueue<E>> createBlownQueue(std::size_t maxSize) override
	{
		return createLockfreeBlownQueue<E>(maxSize);
	}
};

#endif


#ifdef HAVE_XENIUM

template <typename E>
class MichaelScottBlownQueueCreator :
		public BlownQueueCreator<E>
{
public:
	std::unique_ptr<BlownQueue<E>> createBlownQueue(std::size_t maxSize) override
	{
		return createMichaelScottBlownQueue<E>(maxSize);
	}
};

template <typename E>
class RamalheteBlownQueueCreator :
		public BlownQueueCreator<E>
{
public:
	std::unique_ptr<BlownQueue<E>> createBlownQueue(std::size_t maxSize) override
	{
		return createRamalheteBlownQueue<E>(maxSize);
	}
};

template <typename E>
class VyukovBlownQueueCreator :
		public BlownQueueCreator<E>
{
public:
	std::unique_ptr<BlownQueue<E>> createBlownQueue(std::size_t maxSize) override
	{
		return createVyukovBlownQueue<E>(maxSize);
	}

	std::unique_ptr<BlownQueue<E>> createBlownQueueWithMinimalSize() override
	{
		return this->createBlownQueue(2);
	}
};

#endif



template <typename T>
class BlownQueueTest :
		public testing::Test
{
protected:

	inline static const std::size_t MAX_SIZE = 5;

	std::unique_ptr<BlownQueue<long>> queue;


	void SetUp() override
	{
		this->queue = T().createBlownQueue(BlownQueueTest::MAX_SIZE);
	}
};

using BlownQueueCreators = testing::Types<
#ifdef HAVE_ATOMIC_QUEUE
	AtomicBlownQueueCreator<long>
#endif

#if defined(HAVE_ATOMIC_QUEUE)
	,
#endif

#ifdef HAVE_MOODYCAMEL_CONCURRENT_QUEUE
	ConcurrentBlownQueueCreator<long>
#endif

#if defined(HAVE_ATOMIC_QUEUE) || defined(HAVE_MOODYCAMEL_CONCURRENT_QUEUE)
	,
#endif

#ifdef HAVE_BOOST_LOCKFREE
	LockfreeBlownQueueCreator<long>
#endif

#if defined(HAVE_ATOMIC_QUEUE) || defined(HAVE_MOODYCAMEL_CONCURRENT_QUEUE) || defined(HAVE_BOOST_LOCKFREE)
	,
#endif

#ifdef HAVE_XENIUM
	MichaelScottBlownQueueCreator<long>,
	RamalheteBlownQueueCreator<long>,
	VyukovBlownQueueCreator<long>
#endif
>;

TYPED_TEST_SUITE(BlownQueueTest, BlownQueueCreators);


TYPED_TEST(BlownQueueTest, test)
{
	std::default_random_engine generator(time(0));
	std::uniform_int_distribution<long> distribution(LONG_MIN, LONG_MAX);

	std::unordered_set<long> portions;
	std::mutex portionsMutex;

	std::unordered_set<long> retrievedPortions;
	std::mutex retrievedPortionsMutex;

	std::size_t producerCount = BlownQueueTest<TypeParam>::MAX_SIZE * 10;
	std::size_t consumerCount = BlownQueueTest<TypeParam>::MAX_SIZE * 10;
	std::vector<std::thread> producers;
	std::vector<std::thread> consumers;

	std::latch producersLatch(producerCount + 1);
	for (std::size_t i = 0; i < producerCount; i++)
	{
		producers.emplace_back(std::thread
			{
				[this, &distribution, &generator, &portions, &portionsMutex, &producersLatch]
				{
					std::unordered_set<long> myPortions;
					{
						std::lock_guard<std::mutex> portionsMutexLock(portionsMutex);
						while (myPortions.size() < BlownQueueTest<TypeParam>::MAX_SIZE * 10)
						{
							long portion = distribution(generator);
							if (portions.insert(portion).second)
							{
								myPortions.insert(portion);
							}
						}
					}

					producersLatch.count_down();
					producersLatch.wait();
					for (long portion : myPortions)
					{
						this->queue->addPortion(portion);
					}
				}
			});
	}

	ASSERT_EQ(this->queue->getSize(), 0);
	producersLatch.count_down();
	while (this->queue->getSize() == 0);

	for (std::size_t i = 0; i < consumerCount; i++)
	{
		consumers.emplace_back(std::thread
			{
				[this, &retrievedPortions, &retrievedPortionsMutex]
				{
					while (true)
					{
						std::optional<long> portion = this->queue->retrievePortion();
						if (!portion.has_value())
						{
							break;
						}
						{
							std::lock_guard<std::mutex> retrievedPortionsLock(retrievedPortionsMutex);
							retrievedPortions.insert(*portion);
						}
					}
				}
			});
	}

	for (std::thread& producer : producers)
	{
		producer.join();
	}

	this->queue->ensureAllPortionsAreRetrieved();
	ASSERT_EQ(this->queue->getSize(), 0);

	this->queue->stopConsumers(consumerCount);
	for (std::thread& consumer : consumers)
	{
		consumer.join();
	}

	ASSERT_EQ(portions, retrievedPortions);
}

TYPED_TEST(BlownQueueTest, testConsumerBlocking)
{
	std::atomic<bool> consumerDone(false);

	std::thread consumer
		{
			[this, &consumerDone]
			{
				this->queue->retrievePortion();
				consumerDone.store(true);
			}
		};

	std::this_thread::sleep_for(std::chrono::milliseconds(1));

	ASSERT_EQ(consumerDone.load(), false);

	this->queue->addPortion(0l);
	consumer.join();
}

TYPED_TEST(BlownQueueTest, testProducerBlocking)
{
	std::atomic<bool> producerDone(false);

	std::thread producer
		{
			[this, &producerDone]
			{
				for (int i = 0; i <= this->queue->getMaxSize(); i++)
				{
					this->queue->addPortion(0l);
				}
				producerDone.store(true);
			}
		};

	std::this_thread::sleep_for(std::chrono::milliseconds(1));

	ASSERT_EQ(producerDone.load(), false);

	this->queue->retrievePortion();
	producer.join();
}

TYPED_TEST(BlownQueueTest, testControllerBlocking)
{
	std::atomic<bool> controllerDone(false);

	this->queue->addPortion(0l);
	std::thread controller
		{
			[this, &controllerDone]
			{
				this->queue->ensureAllPortionsAreRetrieved();
				controllerDone.store(true);
			}
		};

	std::this_thread::sleep_for(std::chrono::milliseconds(1));

	ASSERT_EQ(controllerDone.load(), false);

	this->queue->retrievePortion();
	controller.join();
}

TYPED_TEST(BlownQueueTest, testMisc)
{
	ASSERT_TRUE(this->queue->getMaxSize() <= BlownQueueTest<TypeParam>::MAX_SIZE);
}

TYPED_TEST(BlownQueueTest, testMultipleMutexesDeadlock)
{
	std::mutex consumerMutex;
	std::condition_variable consumerCondition;
	std::mutex producerMutex;
	std::condition_variable producerCondition;

	this->queue = TypeParam().createBlownQueueWithMinimalSize();
	long count = 1000000;

	long consumedPortions = 0;

	{
		std::unique_lock consumerLock(consumerMutex);
		std::unique_lock producerLock(producerMutex);

		std::thread consumer
			{
				[this, &consumedPortions, &consumerMutex, &consumerCondition]
				{
					while (this->queue->retrievePortion().has_value())
					{
						consumedPortions++;
					}

					{
						std::unique_lock consumerLock(consumerMutex);
						consumerCondition.notify_one();
					}
				}
			};

		std::thread producer
			{
				[this, count, &producerMutex, &producerCondition]
				{
					for (long i = 0; i < count; i++)
					{
						this->queue->addPortion(i);
					}

					{
						std::unique_lock producerLock(producerMutex);
						producerCondition.notify_one();
					}
				}
			};

		std::cv_status producerStatus = producerCondition.wait_for(producerLock, std::chrono::milliseconds(60000));
		ASSERT_EQ(producerStatus, std::cv_status::no_timeout);
		producer.join();

		this->queue->stopConsumers(1);

		std::cv_status consumerStatus = consumerCondition.wait_for(consumerLock, std::chrono::milliseconds(60000));
		ASSERT_EQ(consumerStatus, std::cv_status::no_timeout);
		consumer.join();
	}

	ASSERT_EQ(consumedPortions, count);
}


}	// namespace test::voidland::concurrent::queue
