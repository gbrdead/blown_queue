package org.voidland.concurrent.queue;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.voidland.concurrent.queue.BlownQueue.NonBlockingQueue;


class ConcurrentNonBlockingQueue<E> 
	implements NonBlockingQueue<E>
{
	private ConcurrentLinkedQueue<E> queue;
	
	
	public ConcurrentNonBlockingQueue(int maxSize)
	{
		this.queue = new ConcurrentLinkedQueue<>();
	}
	
    @Override
    public boolean tryEnqueue(E portion)
    {
        return this.queue.offer(portion);
    }
    
    @Override
    public E tryDequeue()
    {
        return this.queue.poll();
    }
}
