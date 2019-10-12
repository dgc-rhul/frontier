# Frontier: A Resilient Edge Processing Platform for the IoT
Frontier is an experimental edge processing platform for the Internet of Things
(IoT) that aims to provide high-throughput data-parallel processing across multiple
edge devices. Further details on Frontier, including a paper that explains the underlying 
model, can be found at the [project website](http://lsds.doc.ic.ac.uk/projects/ita-dsm). The 
research that lead to Frontier was sponsored by the [ITA project](http://nis-ita.org).

Frontier is licensed under EPL (Eclipse Public License). The Frontier system is
under heavy development and should be considered an alpha release. This is not
considered a "stable" branch.

Frontier builds on previous research into data-parallel stream processing for 
datacenter environments as part of the [SEEP project](http://lsds.doc.ic.ac.uk/projects/seep),
albeit heavily modified to operate in an edge environment. 

Below is some information regarding how to build and run Frontier.

## Building 
The project follows the standard Maven directory structure, with two
differentiated modules, seep-system and seep-streamsql.

To build meander, you need `maven` and `ant` installed:

```
sudo apt-get update
sudo apt-get install maven ant
```

Next, you need to set the repository directory `repoDir` in the Frontier
configuration file. For example, if your copy of the Frontier
repository is at `/home/myusername/dev/frontier`, then change the value of
the `repoDir` property in
`/home/myusername/dev/frontier/seep-system/src/main/resources/config.properties`
to `repoDir=/home/myusername/dev/frontier`.

Then from the top level directory:

```./frontier-bld.sh pi```

This will build `seep-system` and the example applications `stateless-simple-query` and
`acita_demo_2015` to execute on Raspberry Pi. Alternatively, to build for the
CORE/EMANE wireless network emulator:

```./frontier-bld.sh core```

## Running
The system requires one master node and N worker nodes (one worker node per
Operator).

First set the IP address of the master node in `mainAddr` inside
`config.properties` and rebuild the system. By default it is `127.0.0.1`
so you don't need to change anything if running in local mode (see below).

Next run the master in the designated node:

```java -jar <system.jar> Master <query.jar> <Base-class>```

where `query.jar` is the compiled query and the last parameter is the name of 
the base class, not a path.

e.g. To run the master for the acita_demo_2015 example:
```
cd seep-system/examples/acita_demo_2015
java -jar lib/seep-system-0.0.1-SNAPSHOT.jar Master `pwd`/dist/acita_demo_2015.jar Base
```

To save stderr/stdout of the master to a log file for debugging in addition to printing to the console,
you can instead invoke the above command with its output piped to `tee`:

```
java -jar lib/seep-system-0.0.1-SNAPSHOT.jar Master `pwd`/dist/acita_demo_2015.jar Base 2>&1 | tee master.log
```

Finally run as many worker nodes as your query requires:

```java -jar <system.jar> Worker```

### Local mode

To run Frontier in a single local machine, append a different port to
each Worker node:

```java -jar <system.jar> Worker <port>```

e.g. For the acita_demo_2015 example:
```
cd seep-system/examples/acita_demo_2015
java -jar lib/seep-system-0.0.1-SNAPSHOT.jar Worker 3501 2>&1 | tee worker1.log
java -jar lib/seep-system-0.0.1-SNAPSHOT.jar Worker 3502 2>&1 | tee worker2.log 
java -jar lib/seep-system-0.0.1-SNAPSHOT.jar Worker 3503 2>&1 | tee worker3.log 
```

Note you will need to run the master and each worker in a different shell. Then follow
the instructions on the master command prompt. 

Specifically, after giving the workers a few seconds to register with the master, enter `1` at the master command prompt.
This will deploy the operators from the query in `src/Base.java` to the workers.
Once that has completed, simply press `2` to start the query, and enter to start the source.
You should see tuples being received in shell output for the worker running the sink operator.

As an alternative to the above 2 steps, you can also enter `7` at the command prompt to deploy the query operators
and start the query immediately.

## Raspberry Pi Face Recognition Instructions
To run one of the face recognition queries on raspberry pi, you must first have built frontier with `./frontier-bld.sh pi`. This will also copy prebuilt javacv jars for arm to the acita_demo_2015 example query's `lib` directory. These jars must be added explicitly to the classpath of the master and workers, as shown in the following steps.

### 1. Master
Start the master using the following command line *from the directory* `seep-system/examples/acita_demo_2015/tmp`:
```
cd tmp
java -classpath "../lib/*" uk.ac.imperial.lsds.seep.Main Master `pwd`/../dist/acita_demo_2015.jar Base 2>&1 | tee master.log
```

N.B. Note the command line is different to before since we are now specifying the classpath explicitly so that it picks up all the jars in `lib`.

### 2. Workers 
#### *Local Mode*
To start multiple workers on the same pi, i.e. in Local Mode, you need to start them with a different port *from the directory* `seep-system/examples/acita_demo_2015/tmp`
```
cd tmp
java -classpath "../lib/*" uk.ac.imperial.lsds.seep.Main  Worker 3501 2>&1 | tee worker1.log

cd tmp
java -classpath "../lib/*" uk.ac.imperial.lsds.seep.Main  Worker 3502 2>&1 | tee worker2.log

cd tmp
java -classpath "../lib/*" uk.ac.imperial.lsds.seep.Main  Worker 3503 2>&1 | tee worker3.log

```

#### *Multiple Pis*
To start each worker on a separate pi, you can omit the explicit port numbers, i.e. on each pi *from the directory* `seep-system/examples/acita_demo_2015/tmp`
```
cd tmp
java -classpath "../lib/*" uk.ac.imperial.lsds.seep.Main Worker 2>&1 | tee worker.log
```

Please see the `Configuration and Logging` section for instructions on how to configure Frontier for
wireless communication over infrastructure mode wifi or ad-hoc wifi.

#### *Multiple Pis, Master on x86 Laptop*
TODO

### 3. Running
Now if you follow the master command prompt as before it should run a face recognition query.
N.B. You must allow sufficient time for step 1 to complete. It may take a couple of minutes for the workers to
train the prediction model on a raspberry pi. When the workers are ready, the master command prompt will reappear.
*Do not proceed to stage 2 at the master before the 2nd command prompt appears!*. Note that as before you can simply
press `7` at the command prompt to deploy and start the query in one go.

## Configuration and Logging
Various properties of Frontier can be configured in `seep-system/src/main/resources/config.properties`.
For example, to change the replication factor and number of tuples sent for the face recognition query you can
modify the following properties in `config.properties` (and then rebuild using `frontier-bld.sh`):
```
replicationFactor=3
...
numTuples=1000
```
As another example, if you are running Frontier on Raspberry Pi, but your Pis are connected via infrastructure mode Wifi instead of ad-hoc mode, you will need to modify the configuration file to tell Frontier to not query the OLSRd routing daemon for ETX weights:

```
piAdHocDeployment=false
disableBackpressureETX=true
```

To avoid having to recompile, you can also override the properties above directly on the command line:

```
java -DreplicationFactor=3 -DnumTuples=1000 -classpath "../lib/*" uk.ac.imperial.lsds.seep.Main Worker
```

Similarly, logging levels (e.g. `DEBUG` or `INFO`) for different packages can be configured in `seep-system/src/main/resources/logback.xml`.
Again, a full rebuild is needed afterward.

TODO: interfacePrefs

# Contributors
Frontier was created by Dan O'Keeffe (formerly in the Large-Scale Distributed Systems (LSDS) group, Imperial College London, now Royal Holloway University of London),
Theodoros Salonidis (IBM Research T.J. Watson), and Peter Pietzuch (LSDS group, Imperial College London).

