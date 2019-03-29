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

package com.thing2x

import javax.management.{ObjectInstance, ObjectName}

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.matching.Regex

package object jmxcli {

  class ObjectNameConverter(name: String) {
    def asObjectName: ObjectName =
      if (name != null && name.length > 0) new ObjectName(name) else null
  }

  implicit def stringToObjectNameConverter(str: String): ObjectNameConverter = new ObjectNameConverter(str)

  val CMD_LINE_ARGS_PATTERN: Regex = "^([^=]+)(?:(?://=)(.+))?$".r

  private[jmxcli] object CommandParse {
    def apply(command: String): Option[CommandParse] = {
      var _cmd: String = ""
      var _args: Seq[String] = Seq.empty

      CMD_LINE_ARGS_PATTERN.findFirstMatchIn(command) match {
        case Some(m) =>
          _cmd = m.group(1)
          if (m.group(2) != null && m.group(2).length > 0)
            _args = m.group(2).split(",")
          else
            _args = Seq.empty
          Some(new CommandParse(_cmd, _args))
        case _ =>
          None
      }
    }
  }

  private[jmxcli] case class CommandParse(cmd: String, args: Seq[String])

  private[jmxcli] case class CommandResult(instance: ObjectInstance, cmd: Command, result: Seq[AnyRef], error: Option[String] = None)

  object CommandBuilder {
    def newBuilder: CommandBuilder = new CommandBuilder
  }

  class CommandBuilder {
    private[jmxcli] val cmds: mutable.Map[String, Seq[Command]] = mutable.Map.empty

    def addCommand(beanname: String): CommandBuilder = {
      addCommand(beanname, "", None)
    }

    def addCommand(beanname: String, cmd: String): CommandBuilder = {
      addCommand(beanname, cmd, None)
    }

    def addCommand(beanname: String, cmd: String, alias: String): CommandBuilder = {
      addCommand(beanname, cmd, Some(alias))
    }

    def addCommand(beanname: String, cmd: String, alias: Option[String]): CommandBuilder = {
      val translatedCmd = if (cmd == "-") "" else cmd
      if (cmds.contains(beanname)) {
        cmds(beanname) :+= Command(translatedCmd, alias)
      }
      else {
        cmds.put(beanname, Seq(Command(translatedCmd, alias)))
      }
      this
    }

    def addCommandFromString(str: String, separator: String): CommandBuilder = {
      val tok = if (separator.length == 1){
        str.split(separator.charAt(0))
      }
      else {
        var buf = str
        var idx = 0
        var rt: Seq[String] = Seq.empty
        do {
          idx = buf.indexOf(separator)
          if (idx >= 0) {
            rt :+= buf.substring(0, idx).trim
            buf = buf.substring(idx+separator.length)
          }
          else {
            rt :+= buf.trim
          }
        } while (idx >= 0 && rt.size <= 3)
        rt.toArray
      }

      if (tok.length == 3)
        addCommand(tok(0), tok(1), tok(2))
      else if (tok.length == 2)
        addCommand(tok(0), tok(1))
      else if (tok.length == 1)
        addCommand(tok(0))
      this
    }

    override def toString: String = {
      cmds.toMap.map{ case(bean, ca) => s"$bean: $ca"}.mkString("\n")
    }
  }

  private[jmxcli] case class Command(cmd: String, alias: Option[String]) {
    def displayName: String = {
      def useAlias: String = if (alias.get == "-") "" else alias.get
      if(alias.isDefined) {
        useAlias
      }
      else {
        if (cmd == "-") {
          if (alias.isDefined) useAlias else  ""
        }
        else {
          cmd
        }
      }
    }
  }

  private[jmxcli] object Command {
    val empty = Command("", None)
  }
}

