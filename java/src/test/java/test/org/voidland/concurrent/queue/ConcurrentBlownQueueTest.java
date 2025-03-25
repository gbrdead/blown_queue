package test.org.voidland.concurrent.queue;

import org.junit.jupiter.api.BeforeEach;
import org.voidland.concurrent.queue.BlownQueue;


public class ConcurrentBlownQueueTest
	extends BlownQueueTest
{
    @BeforeEach
    public void initQueue()
        throws Exception
    {
        this.queue = BlownQueue.createConcurrentBlownQueue(BlownQueueTest.MAX_SIZE);
    }
}
