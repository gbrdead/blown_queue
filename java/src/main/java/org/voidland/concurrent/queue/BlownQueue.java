package org.voidland.concurrent.queue;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@link MPMC_PortionQueue} that internally uses a lock-free queue.<br>
 * If any operation needs waiting, it will block (and the CPU will be yielded to another thread).<br>
 * Blocking is performed as rarely and for as shortly as possible.<br>
 * <br>
 * Obtaining an instance of this queue can be done in one of the following ways:
 * <ol>
 * <li>
 * Using one of the predefined implementations by calling its corresponding factory method. So far, these are:<br>
 * <ul>
 * <li>
 * {@link #createConcurrentBlownQueue(int)} - creates a queue that uses {@link ConcurrentLinkedQueue} internally
 * </li>
 * </ul>
 * </li>
 * <li>
 * Implementing {@link #NonBlockingQueue} for the lock-free queue of your choice and directly calling {@link #BlownQueue(int, NonBlockingQueue)}. 
 * </li>
 * </ol>
 * 
 * @see <a href="https://github.com/gbrdead/blown_queue/blob/master/README.md">rationale</a>
 */
public class BlownQueue<E>
        implements MPMC_PortionQueue<E>
{
	/**
	 * A thin wrapper of a lock-free queue. Its methods must not block (or wait in any other fashion) under any circumstances.<br>
	 * The wrapped queue may be bounded or not. If bounded, use the same maximum size as the one given to {@link #BlownQueue(int, NonBlockingQueue)}.
	 */
	public interface NonBlockingQueue<E>
	{
		/**
		 * Attempts to add a portion to the queue. This method must never block.<br>
		 * If the wrapped queue is unbounded, this method should always return true.
		 * 
		 * @param portion the portion (element) to be added
		 * @return true if the portion was added successfully; false if the maximum size of the queue would be exceeded 
		 */
	    boolean tryEnqueue(E portion);
	    
	    /**
	     * Attempts to retrieve a portion from the queue. This method must never block.<br>
	     * 
	     * @return the retrieved portion (element); null if the queue is empty
	     */
	    E tryDequeue();
	}
	
	
	private AtomicInteger size;
    private int maxSize;
    
    private Object notFullCondition;
    private Object notEmptyCondition;
    private Object emptyCondition;
    
    private AtomicBoolean aProducerIsWaiting;
    private AtomicBoolean aConsumerIsWaiting;
    
    private NonBlockingQueue<E> nonBlockingQueue;
    private boolean workDone;
    
    /**
     * Creates a {@link #BlownQueue} backed by a {@link ConcurrentLinkedQueue}.
     * 
     * @param <E> the type of the portions (elements) in the queue
     * @param maxSize the maximum number of portions in the queue;
     * 		may be exceeded occasionally by up to the number of producer threads
     */
    public static <E> BlownQueue<E> createConcurrentBlownQueue(int maxSize)
    {
    	NonBlockingQueue<E> nonBlockingQueue = new ConcurrentNonBlockingQueue<E>(maxSize);
        return new BlownQueue<E>(maxSize, nonBlockingQueue);
    }
    
    /**
     * Creates a {@link #BlownQueue}.
     *
     * @param nonBlockingQueue a wrapper for the backing lock-free queue
     * @param maxSize the maximum number of portions in the queue;
     * 		may be exceeded occasionally by up to the number of producer threads (but only if the wrapped queue is unbounded)
     */    
    public BlownQueue(int maxSize, NonBlockingQueue<E> nonBlockingQueue)
    {
    	this.size = new AtomicInteger(0);
        this.maxSize = maxSize;
        
        this.notFullCondition = new Object();
        this.notEmptyCondition = new Object();
        this.emptyCondition = new Object();

        this.aProducerIsWaiting = new AtomicBoolean(false);
        this.aConsumerIsWaiting = new AtomicBoolean(false);
        
        this.nonBlockingQueue = nonBlockingQueue;
        this.workDone = false;
    }
    
    @Override
    public void addPortion(E portion)
    		throws InterruptedException
    {
    	if (portion == null)
    	{
    		throw new NullPointerException();
    	}
    	
        while (true)
        {
            if (this.size.get() >= this.maxSize)
            {
                synchronized (this.notFullCondition)
                {
                    while (this.size.get() >= this.maxSize)
                    {
                    	this.aProducerIsWaiting.set(true);
                        this.notFullCondition.wait();
                    }
                }
            }
            
            if (this.nonBlockingQueue.tryEnqueue(portion))
            {
            	break;
            }
        }
        this.size.getAndIncrement();

        if (this.aConsumerIsWaiting.compareAndSet(true, false))
        {
            synchronized (this.notEmptyCondition)
            {
                this.notEmptyCondition.notifyAll();
            }
        }
    }
    
    @Override
    public E retrievePortion()
    		throws InterruptedException
    {
        E portion = this.nonBlockingQueue.tryDequeue();

        if (portion == null)
        {
            synchronized (this.notEmptyCondition)
            {
            	while (true)
            	{
                    if (this.workDone)
                    {
                        return null;
                    }
                    
                    portion = this.nonBlockingQueue.tryDequeue();
                    if (portion != null)
                    {
                    	break;
                    }
                    
                    this.aConsumerIsWaiting.set(true);
                    this.notEmptyCondition.wait();
            	}
            }
        }
        
        int newSize = this.size.decrementAndGet();
        
        if (this.aProducerIsWaiting.compareAndSet(true, false))
        {
            synchronized (this.notFullCondition)
            {
                this.notFullCondition.notifyAll();
            }
        }
        
        if (newSize == 0)
        {
            synchronized (this.emptyCondition)
            {
                this.emptyCondition.notify();
            }
        }

        return portion;
    }
    
    @Override
    public void ensureAllPortionsAreRetrieved()
    		throws InterruptedException
    {
        synchronized (this.notEmptyCondition)
        {
            this.notEmptyCondition.notifyAll();
        }

        synchronized (this.emptyCondition)
        {
            while (this.size.get() > 0)
            {
                this.emptyCondition.wait();
            }
        }
    }

    @Override
    public void stopConsumers(int finalConsumerThreadsCount)
    {
        synchronized (this.notEmptyCondition)
        {
        	this.workDone = true;
            this.notEmptyCondition.notifyAll();
        }
    }
    
    @Override
    public int getSize()
    {
        return this.size.get();
    }
    
    @Override
    public int getMaxSize()
    {
        return this.maxSize;
    }
}
