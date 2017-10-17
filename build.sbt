organization := "de.johoop"
name := "xplane"
version := "0.1"
scalaVersion := "2.12.3"
scalacOptions ++= Seq("-deprecation", "-unchecked", "-language:_", "-target:jvm-1.8", "-encoding", "UTF-8", "-Ywarn-dead-code", "-Ywarn-unused")

val catsVersion = "0.9.0"
val akkaStreamsVersion = "2.5.6"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats" % catsVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaStreamsVersion
)
