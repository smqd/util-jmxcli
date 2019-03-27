package com.thing2x.jmxcli

import javax.management._
import javax.management.openmbean.{CompositeData, TabularData}
import javax.management.remote.{JMXConnector, JMXConnectorFactory, JMXServiceURL}

import scala.collection.JavaConverters._
import scala.language.implicitConversions

object JmxClient extends App {

  val hostport: String = "localhost:9011"
  val login: String = null
  val password: String = null
  val beanname: String = "java.lang:name=PS Old Gen,type=MemoryPool"
  val commands: Seq[String] = Seq("Usage", "UsageThreshold", "PeakUsage")

  val client = new JmxClient(hostport, login, password)

  client.execute(beanname, commands:_*)
}

class JmxClient(hostport: String, login:String, password: String) {

  def execute(): Unit = {
    execute(null, Seq.empty[String]:_*)
  }

  def execute(beanname: String, commands: String*): Unit = {
    val jmxc = jmxConnector(hostport, login, password)
    try {
      doBeans(jmxc.getMBeanServerConnection, beanname.asObjectName, commands.map(Command(_, None)))
    }
    finally {
      jmxc.close()
    }
  }

  def execute(builder: CommandBuilder): Unit = {
    val jmxc = jmxConnector(hostport, login, password)
    try {
      builder.cmds.foreach{ case(beanname, commands) =>
        doBeans(jmxc.getMBeanServerConnection, beanname.asObjectName, commands)
      }
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
              println(s"$header Attribute  ${at.getName} : ${at.getType}")
            case op: MBeanOperationInfo =>
              println(s"$header Operation  ${op.getName}(${op.getSignature.map(p => s"${p.getName}:${p.getType}").mkString(", ")}) : ${op.getReturnType}")
            case n =>
              println(s"$header\n    ${cmd.displayName}: $n")
          }
        }
      }
    }

    def resultCompositeData(indent: String, name: String, data: CompositeData): String = {
      data.getCompositeType.keySet.asScala.map{ k =>
        data.get(k) match {
          case v: CompositeData =>
            resultCompositeData(indent+"    ", s"$name.$k", v)
          case v: TabularData =>
            resultTabularData(indent+"    ", s"$name.$k", v)
          case v =>
            s"$indent$name.$k: ${v.toString}"
        }
      }.mkString(s"$indent    ", s"\n$indent    ", "")
    }

    def resultTabularData(indent: String, name: String, data: TabularData): String = {
      data.values().asScala.map {
        case v: CompositeData =>
          resultCompositeData(indent+"    ", "", v)
        case v: TabularData =>
          resultTabularData(indent+"    ", "", v)
        case v =>
          s"$indent${v.toString}"
      }.mkString(s"$indent    ", s"\n$indent", "")
    }
  }

  private def doBean(mbsc: MBeanServerConnection, instance: ObjectInstance, commands: Seq[Command]): Seq[CommandResult] = {
    if (commands.isEmpty) {
      Seq(listOptions(mbsc, instance))
    }
    else {
      commands.map( doSubCommand(mbsc, instance, _ ) )
    }
  }

  private def listOptions(mbsc: MBeanServerConnection, instance: ObjectInstance): CommandResult = {
    val info = mbsc.getMBeanInfo(instance.getObjectName)
    val attrs = info.getAttributes
    val opers = info.getOperations

    CommandResult(instance, Command.empty, attrs.toSeq ++ opers.toSeq)
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

  private def jmxConnector(hostport: String, login: String, password: String): JMXConnector = {
    val rmiurl = new JMXServiceURL(s"service:jmx:rmi://$hostport/jndi/rmi://$hostport/jmxrmi")
    val cred = Map(JMXConnector.CREDENTIALS -> Array(login, password))
    JMXConnectorFactory.connect(rmiurl, cred.asJava)
  }
}
