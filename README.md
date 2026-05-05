### jkvs - Key Value Store in Java
Building a distributed key-value store database following [Pingcap's Talent Plan](https://github.com/pingcap/talent-plan)

### Clone the repo
```bash
git clone https://github.com/ddanielaiwuyo/jkvs.git ~/jkvs
cd ~/jkvs
```
### Requirements
1. Java 25 

To see the version of java you have installed, I'm on a linux distribution so yours might differ slightly
```bash
    dev::jkvs (main) | java --version 
    openjdk 25.0.2 2026-01-20
    OpenJDK Runtime Environment (Red_Hat-25.0.2.0.10-3) (build 25.0.2+10)
    OpenJDK 64-Bit Server VM (Red_Hat-25.0.2.0.10-3) (build 25.0.2+10, mixed mode, sharing)
```

### Run the application
Now at this point, I've decided not to use Maven, as I just need simple build and run tool that can use as
```bash
jkvs set name persona-mp3
```
so I wrote a simple build script [build.sh](./build.sh)

To use it the format is as simple as `build.sh run <command> <key> <value>?
But while I use it during fast iterations and how I also recommend is aliasing it in your terminal session
```bash
chmod u+x build.sh # make the script executable
build.sh run set username persona-mp3
build.sh run get username
```

To make is simpler, alias the `build.sh run` command in your terminal session as
```bash
alias jkvs="build.sh run"
# And then you can run it as
jkvs set username heiseinberg
jkvs get username
```

To make it persist across sessions
```bash
echo 'alias jkvs="~/jkvs/build.sh run"' >> .bashrc or .zshrc
```
