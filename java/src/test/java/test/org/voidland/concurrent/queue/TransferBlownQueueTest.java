package test.org.voidland.concurrent.queue;

import org.voidland.concurrent.queue.BlownQueue;


public class TransferBlownQueueTest
	extends BlownQueueTest
{
    @Override
    public BlownQueue<Long> createQueue(int maxSize)
        throws Exception
    {
        return BlownQueue.createTransferBlownQueue(BlownQueueTest.MAX_SIZE);
    }
}
