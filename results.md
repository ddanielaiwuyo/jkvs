## Observations after log_compaction

At some point, at about after ~8000 writes, this becomes painfully slow for writes, especially when you cross over 50k writes
Because 1. If all values are unique, there's no need for log compaction, but the file still grows linearly. It's costly to build
500KB worth of data into a HashMap.

2. I assume reads would  be faster, but would also almost cost the same, because you still have to rebuild the index 

But maybe because this isn't a server/long-lived process yet, I assume this would be slightly different
It kinda made more sense, seeing it live, why people say reads get slower, or writes get slower.

Also although, I knew it wasn't concurrent safe at this stage I tried using another tmux window to run the stress.sh script
and almost immediately the log broke, and the app coudln't continue

Now, I've also been thinking about how I want to handle errors like these...
1. How do we proceed after `InvalidLogFormat`. Do we crash and preserve `Correctness`? No matter what data is always correct, otherwise something is wrong
2. Do we ignore it or find a routeabout? Making sure that we can proceed with the daily bread?


For example, during stress testing, mutilple compactions can happen within a second, and we have multiple logs
1. Do we keep everyones logs? Right now, I wanted to get it working, because on one node or in dev mode it doesn't matter, 
   but at some point, this is costly. So do we agree on whos' is correct, or do we have nano-seconds to denote it? Or we do 
   we keep all of them and merge later on,(hope i don't get to do this, feels painful, but interesting)
