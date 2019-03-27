name := "JmxTelegrafInput"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test

enablePlugins(LauncherJarPlugin)

mainClass in assembly := Some("com.thing2x.jmxcli.JmxClient")

assemblyJarName in assembly := "JmxTelegrafInput.jar"

test in assembly := {}