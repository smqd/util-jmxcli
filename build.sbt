name := "JmxClient"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies += "com.github.scopt" %% "scopt" % "4.0.0-RC2"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test

enablePlugins(LauncherJarPlugin)

mainClass in assembly := Some("com.thing2x.jmxcli.JmxClient")

assemblyJarName in assembly := s"JmxClient-${version.value}.jar"

test in assembly := {}