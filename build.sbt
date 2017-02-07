name := "smv"

organization := "org.tresamigos"

version := "2.1-SNAPSHOT"

scalaVersion := "2.11.8"

scalacOptions ++= Seq("-deprecation", "-feature")

val sparkVersion = "2.1.0"

val jettyVersion = "8.1.18.v20150929"

val commonsHttpclientVersion = "4.3.2"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-sql"  % sparkVersion % "provided",
  "org.apache.spark" %% "spark-hive" % sparkVersion % "provided",
  "org.scalatest" %% "scalatest" % "2.2.0" % "test",
  "com.google.guava" % "guava" % "14.0.1",
  "org.rogach" %% "scallop" % "0.9.5",
  "org.eclipse.jetty" % "jetty-server" % jettyVersion,
  "org.eclipse.jetty" % "jetty-client" % jettyVersion,
  "org.apache.httpcomponents" % "httpclient" % commonsHttpclientVersion,
  "org.joda" % "joda-convert" % "1.7",
  "joda-time" % "joda-time" % "2.7",
  "com.rockymadden.stringmetric" %% "stringmetric-core" % "0.27.4"
//  "com.github.mdr" %% "ascii-graphs" % "0.0.6"
)

parallelExecution in Test := false

publishArtifact in Test := true

mainClass in assembly := Some("org.tresamigos.smv.SmvApp")

assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)

assemblyJarName in assembly := s"${name.value}-${version.value}-jar-with-dependencies.jar"

test in assembly := {}
