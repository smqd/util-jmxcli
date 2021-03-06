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

import javax.management._
import javax.management.openmbean.{CompositeData, TabularData}
import javax.management.remote.{JMXConnector, JMXConnectorFactory, JMXServiceURL}
import scopt.OParser

import scala.collection.JavaConverters._
import scala.language.implicitConversions

object JmxClient extends App {

  case class Config(hostPort: String = "localhost:9010",
                    registryHostPort: Option[String] = None,
                    verbose: Boolean = false,
                    silent: Boolean = false,
                    login: Option[String] = None,
                    password: Option[String] = None,
                    commandSeparator: String = "/",
                    commands: Seq[String] = Seq.empty,
                    builder: CommandBuilder = CommandBuilder.newBuilder
                   )

  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName(s"java -jar ${Versions.jmxJarName}"),
      head(s"JmxClient ${Versions.jmxClientVersion}"),
      help("help").text("print this messages"),
      version("version").text("print current version"),
      opt[Unit]('v', "verbose").action( (_, c) => c.copy(verbose = true)),
      opt[Unit]('x', "no-exception").action( (_, c) => c.copy(silent = true))
        .text("no output on exception"),
      opt[String]('s', "server").required().valueName("host:port").action( (x,c) => c.copy(hostPort = x))
        .text("jmx server"),
      opt[String]('r', "rmi").valueName("host:port").action( (x,c) => c.copy(registryHostPort = Some(x)))
        .text("rmi registry"),
      opt[String]('u', "user").valueName("<username>").action( (x,c) => c.copy(login = Some(x)))
        .text("jmx authentication user"),
      opt[String]('p', "password").valueName("<phrase>").action( (x,c) => c.copy(password = Some(x)))
        .text("jmx authentication credential"),
      opt[String]('e', "sep").action( (x,c) => c.copy(commandSeparator = x))
        .text("specify command separator (default is /)"),
      arg[String]("commands ...").unbounded().optional().action{ (x,c) => c.copy(commands = c.commands :+ x) }
        .text("<b1/f1/p1> <b2/f2/p2> <b3/f3> ..."),
      note(
        """    A command is consist of three parts as bean-name/feature/param that are separated by /.
          |    The separator character(default / ) can be customized using '-t' option
          |    - bean-name : mandatory, it specify the target MXBean by name
          |    - feature   : optional, specify attribute or operation
          |                  if feature is attribute name, it will retrieve the value of the attribute
          |                     param is used as alias instead of the real attribute name
          |                     if alias is '-', no prefix and name is appended
          |                  if feature is operation name, it will invoke the operation
          |                     param is comma-separated list of arguments for the operation
          |                  if feature is not specified, JmxClient will display all attributes and operations
          |    - param     : optional, it works differently depends on the feature
          |
          |    ex)
          |      . retrieve thread count
          |        java.lang:type=Threading/ThreadCount
          |
          |      . retrieve memory usage, replace the attribute name with alias 'mem'
          |        java.lang:type=Memory/HeapMemoryUsage/mem
          |
          |      . use quotation mark to encapsulate space character
          |        "java.lang:type=MemoryPool,name=PS Old Gen/PeakUsage/peak"
          |
          |      . invoke MXBean's method, this example will invoke bean.setValue(param1, param2)
          |        com.example:name=SomeMXBean/setValue/param1,param2
          |
          |      . use -e option to avoid confliction with command that contains '/' character
          |        -e $$$ "java.lang:type=SomeMXBean,name=my path$$$/disk1/data/path$$$peak"
        """.stripMargin),
      checkConfig { c =>
        c.commands.foreach(c.builder.addCommandFromString(_, c.commandSeparator))
        success
      }
    )
  }

  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      val client = new JmxClient(config.hostPort, config.registryHostPort, config.login, config.password, config.verbose, config.silent)
      client.execute(config.builder)
    case _ =>
  }

}

class JmxClient(hostPort: String, registryHostPort: Option[String], login:Option[String], password: Option[String], verbose: Boolean = false, silent: Boolean = false) {

  def execute(): Unit = {
    execute(null, Seq.empty[String]:_*)
  }

  def execute(beanname: String, commands: String*): Unit = {
    val jmxc = jmxConnector(hostPort, login, password)
    try {
      doBeans(jmxc.getMBeanServerConnection, beanname.asObjectName, commands.map(Command(_, None)))
    }
    finally {
      jmxc.close()
    }
  }

  def execute(builder: CommandBuilder): Unit = {
    if (verbose) println(s"> jmx connector to '$hostPort' login=$login password=$password")
    val jmxc = jmxConnector(hostPort, login, password)
    try {
      if (verbose) println(s"> commands list\n${builder.toString}")

      val cmds = builder.cmds

      if (cmds.isEmpty) {
        doBeans(jmxc.getMBeanServerConnection, null, Seq.empty)
      }
      else {
        cmds.foreach{ case(beanname, commands) =>
          if (verbose) println(s"> bean: $beanname, commands: $commands")
          doBeans(jmxc.getMBeanServerConnection, beanname.asObjectName, commands)
        }
      }
    }
    catch {
      case e: Throwable =>
        if(!silent) e.printStackTrace()
    }
    finally {
      jmxc.close()
    }
  }

  private def doBeans(mbsc: MBeanServerConnection, objName: ObjectName, commands: Seq[Command]): Unit = {
    val beans = mbsc.queryMBeans(objName, null).asScala

    if (beans.isEmpty) { // no bean found, check if we create a bean?
      Seq.empty
    }
    else if (objName == null) {
      beans.toSeq.sortWith{ (ol, or) => ol.getObjectName.compareTo(or.getObjectName) < 0}.zipWithIndex.foreach { case (b, idx) =>
        println(s"[$idx] ${b.getObjectName.getCanonicalName}")
      }
      Seq.empty
    }
    else {
      val result = beans.flatMap( doBean(mbsc, _, commands) ).toSeq.groupBy(_.instance)

      def printSimple(header: String, name: String, value: String): Unit = {
        if (name.length == 0)
          println(s"$header\n    $value")
        else
          println(s"$header\n    $name: $value")
      }

      result.foreach { case (oi, r) =>
        r.groupBy(_.cmd).foreach { case (cmd, rset) =>
          val header = s"${oi.getObjectName.getCanonicalName} ${cmd.cmd}"
          rset.flatMap(_.result).foreach {
            case cd: CompositeData =>
              println(header)
              println( resultCompositeData("", cmd.displayName, cd) )
            case td: TabularData =>
              println(header)
              println( resultTabularData("", cmd.displayName, td) )
            case at: MBeanAttributeInfo =>
              val prefix = if (cmd.alias.isDefined) cmd.alias.get else header
              println(s"$prefix attribute ${at.getName} : ${at.getType}")
            case op: MBeanOperationInfo =>
              val prefix = if (cmd.alias.isDefined) cmd.alias.get else header
              println(s"$prefix operation ${op.getName}(${op.getSignature.map(p => s"${p.getName}:${p.getType}").mkString(", ")}) : ${op.getReturnType}")
            case n: java.lang.Double =>
              printSimple(header, cmd.displayName, f"${n.doubleValue}%.2f")
            case n: java.lang.Float =>
              printSimple(header, cmd.displayName, f"${n.floatValue}%.2f")
            case n: Array[_] =>
              printSimple(header, cmd.displayName, f"${n.map(_.toString).mkString("[", ", ", "]")}")
            case n =>
              printSimple(header, cmd.displayName, if (n == null) "<null>" else n.toString)
          }
        }
      }
    }

    def resultCompositeData(indent: String, name: String, data: CompositeData): String = {
      val prefix = if(name.length > 0) s"$name." else ""
      data.getCompositeType.keySet.asScala.map{ k =>
        data.get(k) match {
          case v: CompositeData =>
            resultCompositeData(indent+"    ", s"$prefix$k", v)
          case v: TabularData =>
            resultTabularData(indent+"    ", s"$prefix$k", v)
          case v =>
            if (prefix.length == 0)
              s"$indent${v.toString}"
            else
              s"$indent$prefix$k: ${v.toString}"
        }
      }.mkString(s"$indent    ", s"\n$indent    ", "")
    }

    def resultTabularData(indent: String, name: String, data: TabularData): String = {
      val prefix = if(name.length > 0) s"$name." else ""
      data.values().asScala.map {
        case v: CompositeData if v.containsKey("key") && v.containsKey("value") =>
          val key = v.get("key")
          val value = v.get("value")
          s"$indent$prefix$key: ${value.toString.replace("\n", "\\n")}"
        case v: CompositeData =>
          resultCompositeData(indent+"    ", "", v)
        case v: TabularData =>
          resultTabularData(indent+"    ", "", v)
        case v =>
          s"$indent${v.toString}"
      }.mkString(s"$indent    ", s"\n$indent    ", "")
    }
  }

  private def doBean(mbsc: MBeanServerConnection, instance: ObjectInstance, commands: Seq[Command]): Seq[CommandResult] = {
    if (commands.isEmpty) {
      Seq(listOptions(mbsc, instance, Command.empty))
    }
    else if (commands.exists(_.cmd == "")) {
      Seq(listOptions(mbsc, instance, commands.find(_.cmd == "").get))
    }
    else {
      commands.map( doSubCommand(mbsc, instance, _ ) )
    }
  }

  private def listOptions(mbsc: MBeanServerConnection, instance: ObjectInstance, command: Command): CommandResult = {
    val info = mbsc.getMBeanInfo(instance.getObjectName)
    val attrs = info.getAttributes
    val opers = info.getOperations

    CommandResult(instance, command, attrs.toSeq ++ opers.toSeq)
  }

  private def doSubCommand(mbsc: MBeanServerConnection, instance: ObjectInstance, command: Command): CommandResult = {
    if (command.cmd == "destroy") {
      mbsc.unregisterMBean(instance.getObjectName)
      CommandResult(instance, command, Seq.empty)
    }
    else if (command.cmd.startsWith("create=")) {
      println("Error: MXBean already exists")
      CommandResult(instance, command, Seq.empty)
    }
    else {
      val info = mbsc.getMBeanInfo(instance.getObjectName)
      val attrs = info.getAttributes
      val opers = info.getOperations

      if (Character.isUpperCase(command.cmd.charAt(0))) {
        // probably an attribute
        if (!isFeatureInfo(attrs, command.cmd) &&
          isFeatureInfo(opers, command.cmd)) {
          // Its not an attribute name. Looks like its name of an
          // operation.  Try it.
          doBeanOperation(mbsc, instance, command, opers)
        }
        else {
          // Then it is an attribute OR its not an attribute name nor
          // operation name and the below invocation will throw a
          // AttributeNotFoundException.
          doAttributeOperation(mbsc, instance, command, attrs)
        }
      }
      else {
        // Must be an operation.
        if (!isFeatureInfo(opers, command.cmd) &&
          isFeatureInfo(attrs, command.cmd)) {
          // Its not an operation name but looks like it could be an
          // attribute name. Try it.
          doAttributeOperation(mbsc, instance, command, attrs)
        }
        else {
          // Its an operation name OR its neither operation nor attribute
          // name and the below will throw a NoSuchMethodException.
          doBeanOperation(mbsc, instance, command, opers)
        }
      }
    }
  }

  private def doAttributeOperation(mbsc: MBeanServerConnection, instance: ObjectInstance, command: Command, infos: Array[MBeanAttributeInfo]): CommandResult = {
    // Usually we get attributes. If an argument, then we're being asked
    // to set attribute.
    CommandParse(command.cmd) match {
      case Some(parse) =>
        // println(s"Attribute: ${parse.cmd}, ${parse.args}")
        if (parse.args.isEmpty) {
          // Special-casing.  If the subCommand is 'Attributes', then return
          // list of all attributes.
          if (command.cmd.equals("Attributes")) {
            val r = mbsc.getAttributes(instance.getObjectName, infos.map(i => i.getName))
            CommandResult(instance, command, r.asScala)
          }
          else {
            val r = mbsc.getAttribute(instance.getObjectName, parse.cmd)
            CommandResult(instance, command, Seq(r))
          }
        }
        else if (parse.args.length != 1) {
          CommandResult(instance, command, Seq.empty, Some(s"Invalid arguments: ${parse.args}"))
        }
        else {
          // Get first attribute of name 'cmd'. Assumption is no method
          // overrides.  Then, look at the attribute and use its type.

          getFeatureInfo(infos, parse.cmd) match {
            case Some(info) =>
              val c = Class.forName(info.getType).getConstructor(classOf[String])
              val a = new Attribute(parse.cmd, c.newInstance(parse.args(0)))
              mbsc.setAttribute(instance.getObjectName, a)
              CommandResult(instance, command, Seq.empty)
            case None =>
              CommandResult(instance, command, Seq.empty)
          }
          CommandResult(instance, command, Seq.empty)
        }
      case None =>
        CommandResult(instance, command, Seq.empty)

    }
  }

  private def doBeanOperation(mbsc: MBeanServerConnection, instance: ObjectInstance, command: Command, infos: Array[MBeanOperationInfo]): CommandResult = {
    CommandParse(command.cmd) match {
      case Some(parse) =>
        getFeatureInfo(infos, parse.cmd) match {
          case Some(op) =>
            val paramInfos = op.getSignature
            if (paramInfos.length != parse.args.length) {
              CommandResult(instance, command, Seq.empty, Some("Passed param count does not match signature count"))
            }
            else {
              val params = paramInfos.zipWithIndex.map{ case (pinfo, idx) =>
                val c = Class.forName(pinfo.getType).getConstructor(classOf[String])
                c.newInstance(parse.args(idx)).asInstanceOf[AnyRef]
              }
              val signature = paramInfos.map(p => p.getType)

              val rt = mbsc.invoke(instance.getObjectName, parse.cmd, params, signature)
              CommandResult(instance, command, Seq(rt))
            }
          case None =>
            CommandResult(instance, command, Seq.empty, Some(s"Operation ${parse.cmd} not found"))
        }
      case None =>
        CommandResult(instance, command, Seq.empty)
    }
  }

  private def isFeatureInfo(infos: Array[MBeanOperationInfo], cmd: String): Boolean =
    getFeatureInfo(infos.asInstanceOf[Array[MBeanFeatureInfo]], cmd).nonEmpty

  private def isFeatureInfo(infos: Array[MBeanAttributeInfo], cmd: String): Boolean =
    getFeatureInfo(infos.asInstanceOf[Array[MBeanFeatureInfo]], cmd).nonEmpty

  private def getFeatureInfo[T <: MBeanFeatureInfo](infos: Array[T], cmd: String): Option[T] = {
    // Cmd may be carrying arguments.  Don't count them in the compare.
    val index = cmd.indexOf('=')
    val name = if (index > 0) cmd.substring(0, index) else cmd
    infos.find(i => i.getName.equals(name))
  }

  private def jmxConnector(hostport: String, login: Option[String], password: Option[String]): JMXConnector = {
    val rmiServer = s"rmi://$hostport/jmxrmi"  // -Dcom.sun.rmi.remote...에서 지정한 것은 rmiserver 이고
    val rmiRegistry = if (registryHostPort.isDefined) s"rmi://${registryHostPort.get}/jndi" else s"rmi://$hostport/jndi"

    val serviceURL = s"service:jmx:$rmiRegistry/$rmiServer"
    if(verbose) println(s"> serviceURL: $serviceURL")
    val rmiurl = new JMXServiceURL(serviceURL)
    val cred = Map(JMXConnector.CREDENTIALS -> Array(login.getOrElse(null), password.getOrElse(null)))
    JMXConnectorFactory.connect(rmiurl, cred.asJava)
  }
}
