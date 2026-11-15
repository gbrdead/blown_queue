#![allow(non_snake_case)]
#![allow(non_camel_case_types)]

#![feature(mpmc_channel)]


include!("mpmc_portion_queue.rs");
include!("non_blocking_queue.rs");


use std::sync::atomic::AtomicUsize;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::sync::Mutex;
use std::sync::Condvar;
use crossbeam::utils::CachePadded;
use std::sync::MutexGuard;


struct Size
{
    size: AtomicUsize,
    maxSize: usize
}

pub struct BlownQueue<E>
{
    sizes: CachePadded<Size>,
    
    mutex: CachePadded<Mutex<bool>>,
    notFullCondition: CachePadded<Condvar>,
    notEmptyCondition: CachePadded<Condvar>,
    emptyCondition: CachePadded<Condvar>,
    
    aProducerIsWaiting: CachePadded<AtomicBool>,
    aConsumerIsWaiting: CachePadded<AtomicBool>,
    
    nonBlockingQueue: CachePadded<Box<dyn NonBlockingQueue<E>>>
}

impl<E: Send + Sync + 'static> BlownQueue<E>
{
    pub fn createConcurrentBlownQueue(maxSize: usize) -> Self
    {
        let nonBlockingQueue: Box<dyn NonBlockingQueue<E>> = Box::new(ConcurrentPortionQueue::<E>::new(maxSize));
        BlownQueue::new(maxSize, nonBlockingQueue)
    }
    
    pub fn createAsyncMPMC_BlownQueue(maxSize: usize) -> Self
    {
        let nonBlockingQueue: Box<dyn NonBlockingQueue<E>> = Box::new(AsyncMPMC_PortionQueue::<E>::new(maxSize));
        BlownQueue::new(maxSize, nonBlockingQueue)
    }

    
    pub fn new(maxSize: usize, nonBlockingQueue: Box<dyn NonBlockingQueue<E>>) -> Self
    {
        Self
        {
            sizes: CachePadded::new(
                Size
                {
                    size: AtomicUsize::new(0),
                    maxSize: maxSize
                }),
            mutex: CachePadded::new(Mutex::new(false)),
            notFullCondition: CachePadded::new(Condvar::new()),
            notEmptyCondition: CachePadded::new(Condvar::new()),
            emptyCondition: CachePadded::new(Condvar::new()),
            aProducerIsWaiting: CachePadded::new(AtomicBool::new(false)),
            aConsumerIsWaiting: CachePadded::new(AtomicBool::new(false)),
            nonBlockingQueue: CachePadded::new(nonBlockingQueue)
        }
    }
    
    
    fn lockMutexIfNecessary<'a>(&'a self, lock: &mut Option<MutexGuard<'a, bool>>)
    {
        if lock.is_none()
        {
            let _ = lock.insert(self.mutex.lock().unwrap());
        }
    }
    
    fn notifyAllWaitingProducers<'a>(&'a self, lock: &mut Option<MutexGuard<'a, bool>>)
    {
        if self.aProducerIsWaiting.compare_exchange(true, false, Ordering::AcqRel, Ordering::Relaxed).is_ok()
        {
            self.lockMutexIfNecessary(lock);
            self.notFullCondition.notify_all();
        }        
    }
    
    fn notifyAllWaitingConsumers<'a>(&'a self, lock: &mut Option<MutexGuard<'a, bool>>)
    {
        if self.aConsumerIsWaiting.compare_exchange(true, false, Ordering::AcqRel, Ordering::Relaxed).is_ok()
        {
            self.lockMutexIfNecessary(lock);
            self.notEmptyCondition.notify_all();
        }        
    }

        fn waitOnCondition(cond: &Condvar, lock: &mut Option<MutexGuard<'_, bool>>)
    {
        let theLock : Option<MutexGuard<'_, bool>> = lock.take();
        let _ = lock.insert(cond.wait(theLock.unwrap()).unwrap());
    }
}

impl<E: Send + Sync + 'static> MPMC_PortionQueue<E> for BlownQueue<E>
{
    fn addPortion(&self, mut portion: E)
    {
        let mut lock: Option<MutexGuard<'_, bool>> = None;
        
        loop
        {
            if self.sizes.size.load(Ordering::Acquire) >= self.sizes.maxSize
            {
                self.lockMutexIfNecessary(&mut lock);
                
                loop
                {
                    let theOnlyWaitingProducer: bool = self.aProducerIsWaiting.compare_exchange(false, true, Ordering::AcqRel, Ordering::Relaxed).is_ok();
                    
                    if self.sizes.size.load(Ordering::Acquire) < self.sizes.maxSize
                    {
                        if theOnlyWaitingProducer
                        {
                            self.aProducerIsWaiting.store(false, Ordering::Release);
                        }
                        break;
                    }
                    
                    BlownQueue::<E>::waitOnCondition(&self.notFullCondition, &mut lock);
                }
            }
            
            match self.nonBlockingQueue.tryEnqueue(portion)
            {
                Ok(_) => break,
                Err(error) => portion = error
            }
        }
        self.sizes.size.fetch_add(1, Ordering::Release);
        
        self.notifyAllWaitingConsumers(&mut lock);
    }
    
    fn retrievePortion(&self) -> Option<E>
    {
        let mut lock: Option<MutexGuard<'_, bool>> = None;
        
        let mut portion: Option<E> = self.nonBlockingQueue.tryDequeue();
        if portion.is_none()
        {
            self.lockMutexIfNecessary(&mut lock);
            
            loop
            {
                let theOnlyWaitingConsumer: bool = self.aConsumerIsWaiting.compare_exchange(false, true, Ordering::AcqRel, Ordering::Relaxed).is_ok();
                
                portion = self.nonBlockingQueue.tryDequeue();
                if portion.is_some()
                {
                    if theOnlyWaitingConsumer
                    {
                        self.aConsumerIsWaiting.store(false, Ordering::Release);
                    }
                    break;
                }

                let workDone: bool = **lock.as_ref().unwrap();
                if workDone
                {
                    return None;
                }
                
                BlownQueue::<E>::waitOnCondition(&self.notEmptyCondition, &mut lock);
            }
        }
        
        let newSize: usize = self.sizes.size.fetch_sub(1, Ordering::AcqRel) - 1;
        
        self.notifyAllWaitingProducers(&mut lock);
        
        if newSize == 0
        {
            self.lockMutexIfNecessary(&mut lock);
            self.emptyCondition.notify_one();
        }
        
        portion
    }
    
    fn stopConsumers(&self)
    {
        let mut workDone = self.mutex.lock().unwrap();
        *workDone = true;
        self.notEmptyCondition.notify_all();
    }
    
    fn getSize(&self) -> usize
    {
        self.sizes.size.load(Ordering::Relaxed)
    }
    
    fn getMaxSize(&self) -> usize
    {
        self.sizes.maxSize
    }
}
