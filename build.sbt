
val versionString = "0.5-SNAPSHOT"
val jarNameString = s"JmxClient-v$versionString.jar"

name := "JmxClient"

version := versionString

scalaVersion := "2.12.8"

libraryDependencies += "com.github.scopt" %% "scopt" % "4.0.0-RC2"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test

enablePlugins(LauncherJarPlugin, AutomateHeaderPlugin)

mainClass in assembly := Some("com.thing2x.jmxcli.JmxClient")

assemblyJarName in assembly := jarNameString

test in assembly := {}

organizationName := "UANGEL"

startYear := Some(2019)

licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))

headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment)

headerMappings := headerMappings.value + (HeaderFileType.conf -> HeaderCommentStyle.hashLineComment)

fork in Test := true

javaOptions in Test ++= Seq(
  "-Dcom.sun.management.jmxremote",
  "-Dcom.sun.management.jmxremote.port=9011",
  "-Dcom.sun.management.jmxremote.local.only=false",
  "-Dcom.sun.management.jmxremote.authenticate=false",
  "-Dcom.sun.management.jmxremote.ssl=false",
)

sourceGenerators in Compile += Def.task {
  val file = (sourceDirectory in Compile).value / "scala/com/thing2x/jmxcli/Versions.scala"
  IO.write(file,
    s"""// Copyright 2019 UANGEL
       |//
       |// Licensed under the Apache License, Version 2.0 (the "License");
       |// you may not use this file except in compliance with the License.
       |// You may obtain a copy of the License at
       |//
       |//     http://www.apache.org/licenses/LICENSE-2.0
       |//
       |// Unless required by applicable law or agreed to in writing, software
       |// distributed under the License is distributed on an "AS IS" BASIS,
       |// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
       |// See the License for the specific language governing permissions and
       |// limitations under the License.
       |
       |package com.thing2x.jmxcli
       |
       |object Versions {
       |   val jmxClientVersion = "$versionString"
       |   val jmxJarName = "$jarNameString"
       |}
    """.stripMargin
  )
  Seq(file)
}.taskValue
