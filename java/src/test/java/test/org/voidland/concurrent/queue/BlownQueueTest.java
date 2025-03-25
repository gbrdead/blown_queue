package test.org.voidland.concurrent.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.voidland.concurrent.queue.BlownQueue;

import com.google.common.util.concurrent.Uninterruptibles;


public abstract class BlownQueueTest
{
	protected static final int MAX_SIZE = 5;
	
	protected BlownQueue<Integer> queue;
	
	
	@Test
	public void test()
		throws Exception
	{
		Random randomGen = new Random(System.currentTimeMillis());
		Set<Integer> portions = Collections.synchronizedSet(new HashSet<>());
		Set<Integer> retrievedPortions = Collections.synchronizedSet(new HashSet<>());
		
		int producerCount = BlownQueueTest.MAX_SIZE * 10;
		int consumerCount = BlownQueueTest.MAX_SIZE * 10;
		List<Thread> producers = new ArrayList<>(producerCount);
		List<Thread> consumers = new ArrayList<>(consumerCount);
		
		CountDownLatch producersLatch = new CountDownLatch(producerCount + 1);
		for (int i = 0; i < producerCount; i++)
		{
			Thread producer = new Thread(() ->
			{
				Set<Integer> myPortions = new HashSet<>();
				
				while (myPortions.size() < BlownQueueTest.MAX_SIZE * 10)
				{
					int portion = randomGen.nextInt();
					if (portions.add(portion))
					{
						myPortions.add(portion);
					}
				}
				
				producersLatch.countDown();
				Uninterruptibles.awaitUninterruptibly(producersLatch);
				for (int portion : myPortions)
				{
					try
					{
						BlownQueueTest.this.queue.addPortion(portion);
					}
					catch (InterruptedException e)
					{
						throw new RuntimeException(e);
					}
				}
			});
			producers.add(producer);
			producer.start();
		}
		
		assertEquals(0, this.queue.getSize());
		producersLatch.countDown();
		while (this.queue.getSize() == 0);

		for (int i = 0; i < consumerCount; i++)
		{
			Thread consumer = new Thread(() ->
			{
				while (true)
				{
					Integer portion;
					try
					{
						portion = BlownQueueTest.this.queue.retrievePortion();
					}
					catch (InterruptedException e)
					{
						throw new RuntimeException(e);
					}
					if (portion == null)
					{
						break;
					}
					retrievedPortions.add(portion);
				}
			});
			consumers.add(consumer);
			consumer.start();
		}
		
		for (Thread producer : producers)
		{
			Uninterruptibles.joinUninterruptibly(producer);
		}
		
		this.queue.ensureAllPortionsAreRetrieved();
		assertEquals(0, this.queue.getSize());

		this.queue.stopConsumers(consumerCount);
		for (Thread consumer : consumers)
		{
			Uninterruptibles.joinUninterruptibly(consumer);
		}
		
		assertEquals(portions, retrievedPortions);
	}
	
	@Test
	public void testConsumerBlocking()
		throws Exception
	{
		Thread consumer = new Thread(() ->
		{
			try
			{
				BlownQueueTest.this.queue.retrievePortion();
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		});
		consumer.start();
		Uninterruptibles.sleepUninterruptibly(1, TimeUnit.MILLISECONDS);
		
		assertEquals(Thread.State.WAITING, consumer.getState());
		
		this.queue.addPortion(0);
		consumer.join();
	}
	
	@Test
	public void testProducerBlocking()
		throws Exception
	{
		Thread producer = new Thread(() ->
		{
			try
			{
				for (int i = 0; i < BlownQueueTest.MAX_SIZE + 1; i++)
				{
					BlownQueueTest.this.queue.addPortion(0);
				}
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		});
		producer.start();
		Uninterruptibles.sleepUninterruptibly(1, TimeUnit.MILLISECONDS);
		
		assertEquals(Thread.State.WAITING, producer.getState());
		
		this.queue.retrievePortion();
		producer.join();
	}

	@Test
	public void testControllerBlocking()
		throws Exception
	{
		this.queue.addPortion(0);
		
		Thread controller = new Thread(() ->
		{
			try
			{
				BlownQueueTest.this.queue.ensureAllPortionsAreRetrieved();
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		});
		controller.start();
		Uninterruptibles.sleepUninterruptibly(1, TimeUnit.MILLISECONDS);
		
		assertEquals(Thread.State.WAITING, controller.getState());
		
		this.queue.retrievePortion();
		controller.join();
	}

	@Test
	public void testMisc()
		throws Exception
	{
		assertEquals(BlownQueueTest.MAX_SIZE, this.queue.getMaxSize());
        assertThrows(NullPointerException.class, () ->
        {
            this.queue.addPortion(null);
        });
	}
}
