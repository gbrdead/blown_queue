pub trait MPMC_PortionQueue<E> : Send + Sync
{
    fn addPortion(&self, portion: E);
    fn retrievePortion(&self) -> Option<E>;
    
    fn stopConsumers(&self);
    
    fn getSize(&self) -> usize;
    fn getMaxSize(&self) -> usize;
}
