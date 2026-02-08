ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.4"
ThisBuild / organization := "com.distrosim"

lazy val root = (project in file("."))
  .settings(
    name := "DistroSim",
    libraryDependencies ++= Seq(
      // Akka Classic Actors (2.8.8 — latest stable with Scala 3 support)
      "com.typesafe.akka" %% "akka-actor"   % "2.8.8",
      "com.typesafe.akka" %% "akka-testkit" % "2.8.8" % Test,

      // Configuration
      "com.typesafe" % "config" % "1.4.3",

      // JSON (Circe)
      "io.circe" %% "circe-core"    % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser"  % "0.14.6",

      // Google Guava (graph structures, compatible with NetGameSim)
      "com.google.guava" % "guava" % "32.1.3-jre",

      // Logging
      "ch.qos.logback"              % "logback-classic" % "1.4.14",
      "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5",

      // Testing
      "org.scalatest" %% "scalatest" % "3.2.17" % Test
    ),

    // NetGameSim fat JAR (place in lib/ directory)
    Compile / unmanagedJars ++= {
      val libDir = baseDirectory.value / "lib"
      if (libDir.exists) (libDir ** "*.jar").classpath
      else Seq.empty
    },

    // Assembly settings
    assembly / assemblyJarName := "distrosim.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", _*) => MergeStrategy.discard
      case PathList("reference.conf") => MergeStrategy.concat
      case x if x.endsWith("module-info.class") => MergeStrategy.discard
      case _ => MergeStrategy.first
    },

    // JVM settings
    javaOptions ++= Seq("-Xms512M", "-Xmx4G"),
    fork := true,

    // Scala compiler options
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked"
    )
  )
