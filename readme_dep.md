# jkvs 
Building a distributed key-value store database following [Pingcap's Talent Plan](https://github.com/pingcap/talent-plan), which
is intentionally meant for Rust. I'll be porting all the tests too later on down the project as correctness will be more favoured than 
speed

### Clone the repo
```bash
git clone https://github.com/persona-mp3/jkvs.git ~/jkvs
cd ~/jkvs
```

### Requirements
1. At least Java 21 - 25. Run `java --version` in your terminal to see what version you have

2. SDKMAN to manage and install GraalVM and native image for building to binary. Visit [SDKMAN installation](https://sdkman.io/install/).
   If you're not a Unix-based system, installation might be different. Please refer to [Graals Documentation](https://www.graalvm.org/jdk21/getting-started/) 
   and [Native Image's](https://www.graalvm.org/latest/reference-manual/native-image/) manual

3. Maven to manage dependencies and run the application. You can find maven's manual at [Apache Maven](https://maven.apache.org/install.html)

4. [Python](https://www.python.org/downloads/) for simulation testing 


At the end of all installation, `java --version` should look similar to this. I'm using fedora, so this might slightly differ based on your OS
```bash
    dev::jkvs (main) | java --version 
    openjdk 25.0.2 2026-01-20
    OpenJDK Runtime Environment (Red_Hat-25.0.2.0.10-3) (build 25.0.2+10)
    OpenJDK 64-Bit Server VM (Red_Hat-25.0.2.0.10-3) (build 25.0.2+10, mixed mode, sharing)
```

And `python --version`

```bash
  jkvs::jkvs (main) | python --version
  Python 3.14.3
```

### Run the database directly as a jar file
```
cd ~/jkvs
mvn clean compile 
mvn package 
java -cp target/jkvs-1.0-SNAPSHOT.jar github.persona_mp3.Main
```


### Run the database directly as executable
```
mvn package
native-image --no-fallback \
  -H:IncludeResources="log4j2.xml" \
  -jar target/jkvs-1.0-SNAPSHOT.jar \
  jkvs

chmod u+x jkvs
```

### Execute the binary
To set a value to the database, `./jkvs set <key> <value>`
```
./jkvs set username persona-mp3 
```

To retreive a value of a key, `./jkvs get <key>`. If  the key exists or hasn't 
be removed, it returns the value, otherwise null
```
./jkvs get username  # retreives the value of `username`
```

To delete a key, `./jkvs rm <key>`. If  the key exists or hasn't 
be removed, it returns the key, otherwise null
```
./jkvs rm username 
```

To show the version
```
./jkvs -V # prints out the version
```



### Automate build
The custom  build script `compiler.sh` automates running the Maven command and using 
GraalVM to build the Java Project. By default, GraalVM and native-image consume alot 
of resources and are slow in building, mostly because it's trying to bake the JVM into 
the binary, so it will take at least 3minutes to build the `jkvs library` which is `jkvs` or `jkvs.exe`, the client executables
as `jkvs-client` and `jkvs-server` as the serer 

The custom build script, `compiler.sh` automates the build for the core-library, `jkvs` and other executables as follows:
1. `jkvs` core library
2. `jkvs-server` server
3. `jkvs-client` (still in development, please use [tester.py](./tester.py) or the library executable directly to interact with the database)
```
chmod u+x ./compiler.sh

./compiler.sh
```
## Note
The build uses Native Image and Graal, so it takes at 3 mins on average to build all the executables thereby also taking, 
system resources mainly because Native-Image tries to build the JVM into the binary. Due to this reason, I've limited myself to using third-party libraries.


For example, the [ReversedReader](./jkvs/src/main/java/github/persona_mp3/lib/utils/ReversedReader.java) is taken from [ApacheCommons](https://github.com/apache/commons-io/blob/master/src/main/java/org/apache/commons/io/input/ReversedLinesFileReader.java)
as I only needed that single util. 

Using Jackson increased build time and needed extra configuration since the library does runtime reflection

If you want to see live logs, in the [log4j2.xml](./jkvs/src/main/resources/log4j2.xml) change 
```
<Root level="warn"> // change `warn` to `info` or `debug`
<AppenderRef ref="Console"/>
</Root>
```


```
mvn package
native-image --no-fallback \
  -H:IncludeResources="log4j2.xml" \
  -jar target/jkvs-1.0-SNAPSHOT.jar \
  jkvs

chmod u+x jkvs
```
### Run the server
```
./jkvs-server # by default listens on port 9090
```

### Run the client
```
./jkvs-client
```

### Run simulation scripts
```
./tester.py
```


## SEE [BENCHMARKS](./BENCHMARK.md)
