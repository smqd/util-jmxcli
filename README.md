# JmxCli

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Build Status](https://travis-ci.org/smqd/util-jmxcli.svg?branch=master)](https://travis-ci.org/smqd/util-jmxcli)
[![Github Releases](https://img.shields.io/github/downloads/smqd/util-jmxcli/latest/total.svg)](https://github.com/smqd/util-jmxcli/releases/latest)

JmxCli is command line JMX remote client.

## Install & Run

JmxCli is distributed as a fat jar file. so just download the binary file from the releases, and run it as it is.

or build from source

```bash
$ sbt assembly
```

Run JmxClient.jar

```bash
$ java -jar JmxClient-vX.Y.Z.jar -h localhost -p 9010 <options...>
```

### Retrieve available JMX Beans list

`-h` is Jmx remote host address, `-p` is port

```bash
$ java -jar JmxClient-vX.Y.Z.jar -h localhost -p 9010

[0] JMImplementation:type=MBeanServerDelegate
[1] com.sun.management:type=DiagnosticCommand
[2] com.sun.management:type=HotSpotDiagnostic
[3] java.lang:type=ClassLoading
[4] java.lang:type=Compilation
[5] java.lang:name=PS MarkSweep,type=GarbageCollector
[6] java.lang:name=PS Scavenge,type=GarbageCollector
[7] java.lang:type=Memory
[8] java.lang:name=CodeCacheManager,type=MemoryManager
[9] java.lang:name=Metaspace Manager,type=MemoryManager
[10] java.lang:name=Code Cache,type=MemoryPool
[11] java.lang:name=Compressed Class Space,type=MemoryPool
[12] java.lang:name=Metaspace,type=MemoryPool
[13] java.lang:name=PS Eden Space,type=MemoryPool
[14] java.lang:name=PS Old Gen,type=MemoryPool
[15] java.lang:name=PS Survivor Space,type=MemoryPool
[16] java.lang:type=OperatingSystem
[17] java.lang:type=Runtime
[18] java.lang:type=Threading
[19] java.nio:name=direct,type=BufferPool
[20] java.nio:name=mapped,type=BufferPool
[21] java.util.logging:type=Logging
```

### Retrieve list of attributes and operations of a specified MXBean

Use command to specify the MXBean

```bash
$ java -jar JmxClient-vX.Y.Z.jar -h localhost -p 9010 "java.lang:type=Memory"

java.lang:type=Memory  Attribute  ObjectPendingFinalizationCount : int
java.lang:type=Memory  Attribute  HeapMemoryUsage : javax.management.openmbean.CompositeData
java.lang:type=Memory  Attribute  NonHeapMemoryUsage : javax.management.openmbean.CompositeData
java.lang:type=Memory  Attribute  Verbose : boolean
java.lang:type=Memory  Attribute  ObjectName : javax.management.ObjectName
java.lang:type=Memory  Operation  gc() : void
```

### Retrieve an attribute

A command consist of three parts `bean-name`/`feature`/`param` that are separated by `/`.
- `bean-name` is mandatory, it specify the target MXBean
- `feature` optional, specify attribute or operation
       if feature is attribute name, it will retrieve the value of the attribute
          param is used as alias instead of the real attribute name
       if feature is operation name, it will invoke the operation
          param is comma-separated list of arguments for the operation
       if feature is not specified, JmxClient will display all attributes and operations   
- `param` optional, it works differently depends on the feature 

```bash
$ java -jar JmxClient-vX.Y.Z.jar -h localhost -p 9010 "java.lang:type=Memory/HeapMemoryUsage/heap"

java.lang:name=PS Old Gen,type=MemoryPool PeakUsage
    peak.max: 2863661056
    peak.committed: 179306496
    peak.used: 5409184
    peak.init: 179306496
```

### Retrieve multiple attributes

Use multiple commands

```bash
$ java -jar JmxClient-vX.Y.Z.jar -h localhost -p 9010 \ 
  "java.lang:type=Memory/HeapMemoryUsage/heap" \
  "java.lang:type=MemoryPool,name=PS Old Gen/PeakUsage/peak" \
  "java.lang:type=Threading/ThreadCount"

java.lang:name=PS Old Gen,type=MemoryPool PeakUsage
    peak.max: 2863661056
    peak.committed: 179306496
    peak.used: 5409184
    peak.init: 179306496
java.lang:type=Memory HeapMemoryUsage
    heap.init: 268435456
    heap.committed: 257425408
    heap.max: 3817865216
    heap.used: 16483304
java.lang:type=Threading ThreadCount
    ThreadCount: 16
``` 
