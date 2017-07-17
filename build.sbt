name := """akka-contextual-actor"""

organization := "com.github.ktonga"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += "Typesafe Snapshot Repository" at "http://repo.typesafe.com/typesafe/snapshots/"

val akkaVersion = "2.4.11"

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion
    ,"com.typesafe.akka" %% "akka-testkit" % akkaVersion
    ,"com.typesafe.akka"   %% "akka-slf4j"       % akkaVersion
    ,"org.scalactic" %% "scalactic" % "3.0.1"
    ,"org.scalatest" %% "scalatest" % "3.0.1" % "test"
)
