package org.voidland.concurrent.queue;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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
    
    private ReentrantLock mutex;
    private Condition notFullCondition;
    private Condition notEmptyCondition;
    private Condition emptyCondition;
    
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
        
        this.mutex = new ReentrantLock();
        this.notFullCondition = this.mutex.newCondition();
        this.notEmptyCondition = this.mutex.newCondition();
        this.emptyCondition = this.mutex.newCondition();

        this.aProducerIsWaiting = new AtomicBoolean(false);
        this.aConsumerIsWaiting = new AtomicBoolean(false);
        
        this.nonBlockingQueue = nonBlockingQueue;
        this.workDone = false;
    }
    
    private void lockMutexIfNecessary()
    		throws InterruptedException
    {
    	if (!this.mutex.isHeldByCurrentThread())
    	{
    		this.mutex.lockInterruptibly();
    	}
    }
    
    private void notifyAllWaitingProducers()
    	throws InterruptedException
    {
        if (this.aProducerIsWaiting.compareAndSet(true, false))
        {
            this.lockMutexIfNecessary();
            this.notFullCondition.signalAll();
        }
    }
    
    private void notifyAllWaitingConsumers()
    	throws InterruptedException
    {
	    if (this.aConsumerIsWaiting.compareAndSet(true, false))
	    {
	    	this.lockMutexIfNecessary();
	        this.notEmptyCondition.signalAll();
	    }
    }
    
    private void unlockMutexIfNecessary()
    {
    	if (this.mutex.isHeldByCurrentThread())
    	{
    		this.mutex.unlock();
    	}
    }

    @Override
    public void addPortion(E portion)
    		throws InterruptedException
    {
    	if (portion == null)
    	{
    		throw new NullPointerException();
    	}

    	try
    	{
		    while (true)
		    {
		        if (this.size.get() >= this.maxSize)
		        {
		            this.lockMutexIfNecessary();
		            
		            while (true)
		            {
		            	boolean theOnlyWaitingProducer = this.aProducerIsWaiting.compareAndSet(false, true);
		            	
		            	if (this.size.get() < this.maxSize)
		            	{
		            		if (theOnlyWaitingProducer)
		            		{
		            			this.aProducerIsWaiting.set(false);
		            		}
		            		break;
		            	}
		            	
		                this.notFullCondition.await();
		            }
		        }
		        
		        if (this.nonBlockingQueue.tryEnqueue(portion))
		        {
		        	break;
		        }
		    }
		    this.size.getAndIncrement();

		    this.notifyAllWaitingConsumers();
    	}
    	finally
    	{
    		this.unlockMutexIfNecessary();
    	}
    }
    
    @Override
    public E retrievePortion()
    		throws InterruptedException
    {
    	try
    	{
	        E portion = this.nonBlockingQueue.tryDequeue();
	        if (portion == null)
	        {
	        	this.lockMutexIfNecessary();
	        	
	        	while (true)
	        	{
	        		boolean theOnlyWaitingConsumer = this.aConsumerIsWaiting.compareAndSet(false, true);
	        		
	                portion = this.nonBlockingQueue.tryDequeue();
	                if (portion != null)
	                {
	                	if (theOnlyWaitingConsumer)
	                	{
	                		this.aConsumerIsWaiting.set(false);
	                	}
	                	break;
	                }
	
	                if (this.workDone)
	                {
	                    return null;
	                }
	                
	                this.notEmptyCondition.await();
	        	}
	        }
	        
	        int newSize = this.size.decrementAndGet();
	        
	        this.notifyAllWaitingProducers();
	        
	        if (newSize == 0)
	        {
	        	this.lockMutexIfNecessary();
	            this.emptyCondition.signal();
	        }
	
	        return portion;
    	}
    	finally
    	{
    		this.unlockMutexIfNecessary();
    	}
    }
    
    @Override
    public void ensureAllPortionsAreRetrieved()
    		throws InterruptedException
    {
    	this.mutex.lockInterruptibly();
    	try
    	{
            this.notEmptyCondition.signalAll();
            while (this.size.get() > 0)
            {
                this.emptyCondition.await();
            }
        }
    	finally
    	{
    		this.mutex.unlock();
    	}
    }

    @Override
    public void stopConsumers(int finalConsumerThreadsCount)
    		throws InterruptedException
    {
    	this.mutex.lockInterruptibly();
    	try
    	{
    		this.workDone = true;
    		this.notEmptyCondition.signalAll();
    	}
    	finally
    	{
    		this.mutex.unlock();
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
