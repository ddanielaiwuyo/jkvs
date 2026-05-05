## in-memory-index
* map of keys to log pointers,

So when a command like `set` is received, we can write to the `.wal` file as 
```
set   username        "persona-mp3"
set   specialization  "distributed_systems"
```
And then append to the `index` file as 
```
username 0
specialization 90
```


So when command is `get` <key>
1. Rebuild `in-memory index` with `index` file
2. check if the key-exists, if exists, use the log pointer to retrive value


So when command is `rm` <key>
1. Rebuild `in-memory index` with `index` file
2. check if the key-exists, if not exists, exit(0)
3. append `rm` command to log as 

```
rm username
```

### Log Compaction
I'm thinking right, the log-compaction should be straight foward, at least within the scope 
I have in mind, 

If we ever wanted to do that, we could just reload the index file, 
and then just copy those offsets into a new log-file, leaving the redundants and stuff.
Why?
1. HashMaps, by default only allow a single value, so the last key entry will always be the 
   fist in a hashmap. This eliminates duplicates for us.

Concern:: Reading a whole index file into memory isn't always the best thing, so the previous approach isn't the best


2. We can go ahead of this, by 
    1. Keeping another log_file, where for each set, we write to both to these. If 
    the set is simply an update, we don't append to the second file, but we just overwrite it.
    2. If there's a rm command, we simply remove it from the second file. 
    But this requires that we have two-files to read and write from, which at anytime could fail. 
    While the main.log is fine and possibly correct, the append.log might not be.


3. Or when we want to compact, we can read the main.log file in reverse, and append them to the new logfile 
    but instead we'll use a HashMap. For each line we read, we append it to the HashMap. And then write everything to the new index file

    Scenarios:
    1. If we encounter a <rm> <key> log before the log itself, we'll have an ignore_list that we can compare each log_line against. 
    2. If we encounter a <rm> <key> log after the log iteself, we ignore it



In all of these scenarions, we have to maintain two-files at a time. And copying and reading could fail at anytime
If we were to do it inplace, this would pose more risk, as the original log file is corrupted and data can't be replayed/rebuilt
