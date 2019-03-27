// Copyright 2019 UANGEL
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.thing2x.jmxcli

import org.scalatest.{BeforeAndAfterAll, FlatSpec}

class JmxClientTest extends FlatSpec with BeforeAndAfterAll {

  private var jmx: JmxClient = _

  override def beforeAll(): Unit = {
    val hostport: String = "127.0.0.1:9011"
    val login: Option[String] = None
    val password: Option[String] = None
    jmx = new JmxClient(hostport, login, password)
  }

  override def afterAll(): Unit = {
  }

  "jmx client" should "work" in {
    jmx.execute()
  }

  it should "work with bean name only" in {
    jmx.execute("java.lang:type=Memory")
  }

  it should "work with one bean and multiple commands" in {
    jmx.execute("java.lang:type=Memory", "HeapMemoryUsage", "Verbose")
  }

  it should "work without command" in {
    jmx.execute("java.lang:name=PS Old Gen,type=MemoryPool")
  }

  it should "work with a single command" in {
    jmx.execute("java.lang:name=PS Old Gen,type=MemoryPool", "Usage")
  }

  it should "work with multiple commands" in {
    jmx.execute("java.lang:name=PS Old Gen,type=MemoryPool", "Usage", "UsageThreshold", "PeakUsage")
  }

  it should "work with compund beans and commands" in {
    val builder = CommandBuilder.newBuilder
      .addCommand("java.lang:type=Memory", "HeapMemoryUsage", "heap")
      .addCommand("java.lang:type=MemoryPool,name=PS Old Gen", "Usage", "old")
      .addCommand("java.lang:type=MemoryPool,name=PS Old Gen", "PeakUsage", "old.peak")
      .addCommand("java.lang:type=MemoryPool,name=PS Eden Space", "Usage", "eden")
      .addCommand("java.lang:type=MemoryPool,name=PS Survivor Space", "Usage", "survivor")
      .addCommand("java.lang:type=Threading", "ThreadCount", "thread.count")
      .addCommand("java.lang:type=OperatingSystem", "OpenFileDescriptorCount", "fd.count")

    jmx.execute(builder)
  }

  it should "work with command arguments" in {
    val builder = CommandBuilder.newBuilder
        .addCommandFromString("java.lang:type=Memory/HeapMemoryUsage/mem")
    jmx.execute(builder)
  }
}
