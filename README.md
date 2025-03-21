# BlownQueue
## (BLocking Only When Necessary Queue)

**BlownQueue** is a multiple producer - multiple consumer (MPMC) portion queue. It is a compromise that combines the high throughput of a lock-free queue, the memory restraints of a bounded queue and the low CPU load of a blocking queue.

## Rationale

1. A queue is either lock-free or blocking.
    - Lock-free queues scale much better with hardware parallelism than blocking ones.
    - The throughput of lock-free queues is higher even on a 2-CPU system.
    - The statements above are based on the measurements from the [Silo](https://github.com/gbrdead/silo) project.
2. A queue is either bounded or non-bounded.
    - Non-bounded queues are inherently unsafe - if the producers are faster than the consumers for a sufficiently long time then such a queue will allocate all available memory.
3. A bounded queue may or may not block when it hits one of its bounds.
    - A consumer cannot proceed when the queue is empty.
    - A producer cannot proceed when the queue is full.
    - Lock-free queues do not block.
    - Active waiting is not a good option - it wastes a CPU that may be used for something beneficial by another thread.
    
A theoretically ideal producer-consumer queue would be:
- lock-free
- bounded
- not using a CPU when waiting on its bounds

## Implementation
    
**BlownQueue** is a mostly lock-free queue that blocks only when its size is close to one of its bounds.
- A **producer**|*consumer* will be blocked until the queue becomes **non-full**|*non-empty*.
- While a producer or a consumer is blocked, it does not use CPU resources.

If the queue stays (almost) **full**|*empty* for some time then the **producers**|*consumers* will be blocked more frequently than the **consumers**|*producers*. This will give relatively more CPU resources to the **consumers**|*producers* until the queue is no longer close to **full**|*empty* and the need for blocking will go away.

**BlownQueue** itself is just a wrapper of a lock-free queue restricting the latter's size by blocking.

**BlownQueue** is not ideal because:
1. A **consumer**|*producer* may be blocked for a short while even if the queue is not **empty**|*full*. This may happen only when the size of the queue is close to **empty**|*full*.
2. It may overflow its upper bound, at maximum by the number of the producers. This may happen only if the underlying queue is not bounded.
3. If the queue is close to **full**|*empty*, even **consumers**|*producers* are occasionally blocked for a short while.