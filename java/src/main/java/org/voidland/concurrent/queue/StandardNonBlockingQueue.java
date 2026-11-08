package org.voidland.concurrent.queue;

import java.util.Queue;

import org.voidland.concurrent.queue.BlownQueue.NonBlockingQueue;


class StandardNonBlockingQueue<E> 
	implements NonBlockingQueue<E>
{
	private Queue<E> queue;
	
	
	public StandardNonBlockingQueue(Queue<E> nonBlockingQueue)
	{
		this.queue = nonBlockingQueue;
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
