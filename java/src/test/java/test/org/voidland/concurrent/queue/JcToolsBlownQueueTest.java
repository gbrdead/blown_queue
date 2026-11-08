package test.org.voidland.concurrent.queue;

import org.voidland.concurrent.queue.BlownQueue;


public class JcToolsBlownQueueTest
	extends BlownQueueTest
{
    @Override
    public BlownQueue<Long> createQueue(int maxSize)
        throws Exception
    {
        return BlownQueue.createJcToolsBlownQueue(BlownQueueTest.MAX_SIZE);
    }
}
