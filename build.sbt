import ReleaseTransformations._

version := (version in ThisBuild).value


name := "s3-stream-factory"

organization := "org.example"

scalaVersion := "2.12.10"

resolvers in ThisBuild += "confluent" at "https://packages.confluent.io/maven/"
resolvers in ThisBuild += "confluent2" at "https://repo.maven.apache.org/maven2/"
resolvers in ThisBuild += Resolver.jcenterRepo
resolvers in ThisBuild ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

val confluentVer = "5.1.2"
val akkaVer = "2.5.23"
val akkaStreamKafkaVer = "1.0.4"
val circeVer = "0.11.1"

libraryDependencies ++= Seq(

  //to bring latest akka streams dependencies
  "com.typesafe.akka" %% "akka-stream-kafka" % akkaStreamKafkaVer,

  //logs
  "com.typesafe.akka" %% "akka-slf4j" % akkaVer,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",

  //kafka
  "org.apache.avro" % "avro" % "1.8.2",
  "io.confluent" % "kafka-avro-serializer" % confluentVer,
  "io.confluent" % "kafka-streams-avro-serde" % confluentVer,

  //circe
  "io.circe" %% "circe-core" % circeVer,
  "io.circe" %% "circe-generic" % circeVer,
  "io.circe" %% "circe-parser" % circeVer,
  "io.circe" %% "circe-generic-extras" % circeVer,

  //akka-streams
  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "1.1.2"
    exclude("com.typesafe.akka", "akka-stream_2.12")
    exclude("com.typesafe.akka", "akka-protobuf_2.12")
    exclude("com.typesafe.akka", "akka-actor_2.12")
    exclude("com.typesafe.akka", "akka-slf4j_2.12"),

  //config
  "com.github.pureconfig" %% "pureconfig" % "0.9.1",

  //test
  "io.github.embeddedkafka" %% "embedded-kafka" % "2.1.1" % Test,
  "org.scalatest" %% "scalatest" % "3.0.7" % Test,
  "org.mockito" % "mockito-inline" % "2.27.0" % Test,
  "org.hamcrest" % "hamcrest-library" % "2.1" % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVer % Test,
  "com.github.pathikrit" %% "better-files" % "3.8.0" % Test,
  "com.lightbend.akka" %% "akka-stream-alpakka-json-streaming" % "1.0.2" % Test
    exclude("com.typesafe.akka", "akka-stream_2.12")
    exclude("com.typesafe.akka", "akka-protobuf_2.12")
    exclude("com.typesafe.akka", "akka-actor_2.12")
    exclude("com.typesafe.akka", "akka-slf4j_2.12"),

)


parallelExecution in Test := false
val nexusRepo = "https://nexus.mobilityservices.io"

credentials += {
  (sys.env.get("USERNAME"), sys.env.get("PASSWORD")) match {
    case (Some(user: String), Some(pwd: String)) =>
      Credentials("Sonatype Nexus Repository Manager", "nexus.***.io", user, pwd)
    case _ => Credentials(Path.userHome / ".sbt" / ".nexuscredentials")
  }
}

publishTo := {
  if (isSnapshot.value)
    Some("snapshots" at s"$nexusRepo/repository/mvn-das-snaphsots/")
  else
    Some("releases" at s"$nexusRepo/repository/mvn-das-releases/")
}

val commitRefName: Option[String] = sys.env.get("CI_COMMIT_REF_NAME")

releaseProcess := {
  if (commitRefName.contains("develop")) {
    Seq[ReleaseStep](ReleaseStep(releaseStepTask(publish in Universal)))
  } else {
    Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      ReleaseStep(releaseStepTask(publish in Universal)),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  }
}