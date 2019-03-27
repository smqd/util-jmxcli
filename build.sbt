
val versionString = "0.1"
val jarNameString = s"JmxClient-$versionString.jar"

name := "JmxClient"

version := versionString

scalaVersion := "2.12.8"

libraryDependencies += "com.github.scopt" %% "scopt" % "4.0.0-RC2"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test

enablePlugins(LauncherJarPlugin)

mainClass in assembly := Some("com.thing2x.jmxcli.JmxClient")

assemblyJarName in assembly := jarNameString

test in assembly := {}

sourceGenerators in Compile += Def.task {
  val file = (sourceDirectory in Compile).value / "scala/com/thing2x/jmxcli/Versions.scala"
  IO.write(file,
    s"""
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
