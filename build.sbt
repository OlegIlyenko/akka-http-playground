name := "akka-http-playground"
version := "0.1.0-SNAPSHOT"

description := "Playing with akka-http and reactive streams"

scalaVersion := "2.11.6"
scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies ++= Seq(
  "org.scaldi" %% "scaldi" % "0.5.6",
  "com.typesafe.akka" %% "akka-http-experimental" % "1.0-RC3",
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "1.0-RC3",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "com.typesafe.akka" %% "akka-stream-testkit-experimental" % "1.0-RC3" % "test"
)

Revolver.settings