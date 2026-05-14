## Concurrency Design
1. Level-1 threads -> Reader virtualThreads. These are usually from the connectionThread
2. 0-Level thread -> Single writer thread that updates map, and does IO Operations
3. Java ConcurrentHashMap for the inMemoryIndex


The aim here is to have eventually reach to a `wait/lock` free system for the readers. I don't like 
the overhead of maintaining `Mutexes` or `Locks` across the codebase


