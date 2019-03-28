#!/usr/bin/env bash

host=`hostname`
jmxhost=127.0.0.1
jmxport=9010

jmxcli="java -jar target/scala-2.12/JmxClient-v0.2.jar -s $jmxhost:$jmxport"

cmds=(
  "java.lang:type=Memory/HeapMemoryUsage/heap"
  "java.lang:type=MemoryPool,name=PS Old Gen/Usage/old"
  "java.lang:type=MemoryPool,name=PS Eden Space/Usage/eden"
  "java.lang:type=MemoryPool,name=PS Survivor Space/Usage/survivor"
  "java.lang:type=Threading/ThreadCount/thread.count"
  "java.lang:type=OperatingSystem/OpenFileDescriptorCount/fd.count"
)

result=`echo $(printf "'%s' " "${cmds[@]}") | xargs $jmxcli`

heap=`echo "$result" | grep heap.max | sed -e 's/^[ \t]*//' | cut -f2 -d" "`
used=`echo "$result" | grep heap.used | sed -e 's/^[ \t]*//' | cut -f2 -d" "`
eden=`echo "$result" | grep eden.used | sed -e 's/^[ \t]*//' | cut -f2 -d" "`
old=`echo "$result" | grep old.used | sed -e 's/^[ \t]*//' | cut -f2 -d" "`
survivor=`echo "$result" | grep survivor.used | sed -e 's/^[ \t]*//' | cut -f2 -d" "`
fdcount=`echo "$result" | grep fd.count | sed -e 's/^[ \t]*//' | cut -f2 -d" "`
thread=`echo "$result" | grep thread.count | sed -e 's/^[ \t]*//' | cut -f2 -d" "`

echo "jvmstat,host=${host} jvm.heap.total=$heap,jvm.heap.used=$used,jvm.heap.old_gen=$old,jvm.heap.eden_space=$eden,jvm.heap.survivor_space=$survivor,jvm.thread.count=$thread,jvm.fd.count=$fdcount"
