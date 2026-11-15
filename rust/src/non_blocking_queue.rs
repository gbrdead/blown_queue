use concurrent_queue::ConcurrentQueue;
use core::option::Option;
use std::result::Result;
use std::sync::mpmc::channel;
use std::sync::mpmc::Sender;
use std::sync::mpmc::Receiver;



pub trait NonBlockingQueue<E> : Send + Sync
{
    fn tryEnqueue(&self, portion: E) -> Result<(), E>;
    fn tryDequeue(&self) -> Option<E>;
}



struct ConcurrentPortionQueue<E>
{
    queue: ConcurrentQueue<E>
}

impl<E> ConcurrentPortionQueue<E>
{
    pub fn new(maxSize: usize) -> Self
    {
        Self
        {
            queue: ConcurrentQueue::<E>::bounded(maxSize)
        }
    }
}

impl<E: Send + Sync> NonBlockingQueue<E> for ConcurrentPortionQueue<E>
{
    fn tryEnqueue(&self, portion: E) -> Result<(), E>
    {
        match self.queue.push(portion)
        {
            Ok(_) => Ok(()),
            Err(error) => Err(error.into_inner())
        }
    }
    
    fn tryDequeue(&self) -> Option<E>
    {
        match self.queue.pop()
        {
            Ok(portion) => Some(portion),
            Err(_) => None
        }
    }
}



struct AsyncMPMC_PortionQueue<E>
{
    sender: Sender<E>,
    receiver: Receiver<E>
}

impl<E> AsyncMPMC_PortionQueue<E>
{
    pub fn new(_maxSize: usize) -> Self
    {
        let (sender, receiver): (Sender<E>, Receiver<E>) = channel::<E>();
        Self
        {
            sender: sender,
            receiver: receiver
        }
    }
}

impl<E: Send + Sync> NonBlockingQueue<E> for AsyncMPMC_PortionQueue<E>
{
    fn tryEnqueue(&self, portion: E) -> Result<(), E>
    {
        let _ = self.sender.send(portion);
        Ok(())
    }
    
    fn tryDequeue(&self) -> Option<E>
    {
        match self.receiver.try_recv()
        {
            Ok(portion) => Some(portion),
            Err(_) => None
        }
    }
}
