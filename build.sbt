import sbt.Keys.{fork, name, scalaVersion}

val catsVersion = "0.9.0"
val akkaStreamsVersion = "2.5.6"
val specs2Version = "4.0.0"

lazy val commonSettings = Seq(
  organization := "de.johoop",
  version := "0.1",

  scalaVersion := "2.12.4",
  scalacOptions ++= Seq("-deprecation", "-unchecked", "-language:_", "-target:jvm-1.8", "-encoding", "UTF-8", "-Ywarn-dead-code", "-Ywarn-unused"),

  javaOptions ++= Seq("-Djava.net.preferIPv4Stack=true"),

  fork in Test := true,
  scalacOptions in Test ++= Seq("-Yrangepos"),

  fork in run := true,
  connectInput in run := true,
  cancelable in Global := true
)

lazy val root = (project in file("."))
  .settings(
    commonSettings,
    name := "xplane-udp",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats" % catsVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaStreamsVersion,

      "org.specs2" %% "specs2-core" % specs2Version % Test
    )
  )

lazy val samples = (project in file("samples"))
  .dependsOn(root)
  .settings(
    commonSettings,
    name := "xplane-udp-samples"
  )
