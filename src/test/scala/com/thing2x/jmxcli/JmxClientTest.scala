package com.thing2x.jmxcli

import org.scalatest.{BeforeAndAfterAll, FlatSpec}

class JmxClientTest extends FlatSpec with BeforeAndAfterAll {

  private var jmx: JmxClient = _

  override def beforeAll(): Unit = {
    val hostport: String = "localhost:9011"
    val login: String = null
    val password: String = null
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
      .addCommand("java.lang:type=MemoryPool,name=PS Eden Space", "Usage", "eden")
      .addCommand("java.lang:type=MemoryPool,name=PS Survivor Space", "Usage", "survivor")
      .addCommand("java.lang:type=Threading", "ThreadCount", "thread.count")
      .addCommand("java.lang:type=OperatingSystem", "OpenFileDescriptorCount", "fd.count")

    jmx.execute(builder)
  }
}
