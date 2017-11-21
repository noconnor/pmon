# pmon (in POC phase)

Pmon is a java based process monitoring application. It uses `lsof` or `netstat` to identify all processes running on a
users system and builds a graph representation of those processes.

**Features TODO**

* Build a simple application server to serve html report so application can be run on remote machines (consider [spark](http://sparkjava.com/documentation#getting-started))
* Add `netstat` support for running on `linux/unix` based systems
* Potentially add ability to disable a connection using `iptables` on a remote host
* Html interface should periodically refresh
* Add functionality to show historical connections
* Group connections from processes based on target host


## Example output from POC

![Process Report](https://raw.githubusercontent.com/noconnor/pmon/master/src/common/images/example.png "Example pmon html drill down")  


## Running POC

Build project : `./gradlew clean compileJava`

From IDE (intellij) run the main function in `Lsof.java`

Open `template.html` report in `<PROJECT_ROOT>/out/production/resources`
