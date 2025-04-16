package test.org.voidland.concurrent.queue;

import org.voidland.concurrent.queue.BlownQueue;


public class ConcurrentBlownQueueTest
	extends BlownQueueTest
{
    @Override
    public BlownQueue<Long> createQueue(int maxSize)
        throws Exception
    {
        return BlownQueue.createConcurrentBlownQueue(BlownQueueTest.MAX_SIZE);
    }
}
