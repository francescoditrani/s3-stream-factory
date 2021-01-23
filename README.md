# S3 Stream Factory


This library provides a factory to generate an akka streams graph that will listen to a configured Kafka topic for event referencing new s3 objects.
Once a new event is published to Kafka, it will stream the reference object(s) to the provided Sink and handle the commit of the source in case of success.
 
The graph is parametrised for "s3 event type" and "Sink Input type". The library already provides two implementations:
 - one with DriveData event type that streams single objects as bytestring; 
 - one with more generic S3 events that streams objects under a prefix as stream of s3 sources.
 
It's possible of course to add new implementations. In case makes sense to reuse your one, please consider adding it to this lib.

 
![](doc/s3%20stream%20factory.png)

Here an example of usage of this lib => [example](https://gitlab.mobilityservices.io/am/roam/perception/drive-data-pipeline/dms-das-perception-mapmatcher-feeder/blob/develop/src/main/scala/dms/das/Application.scala)
 
##  Usage (for build.sbt)

This library is actually compiled against scala 2.12

These are other versions used for the main dependencies (in case of different versions in the host application, could be necessary to shade these):
```
Confluent platform = "5.1.2"
Akka = "2.5.23"
Akka Streams Kafka = "1.0.4"
Circe = "0.11.1"
Alpakka S3" = "1.0.1"
```

To use this library, add to your dependecies:
```scala
"org.example" %% "s3-stream-factory" % "x.x.x"
``` 
Add these repositories:
```
resolvers += "dasNexusSnapshots" at "https://nexus.mobilityservices.io/repository/mvn-das-snaphsots/"
resolvers += "dasNexusReleases" at "https://nexus.mobilityservices.io/repository/mvn-das-releases/"
```

And the credentials with something like:

```scala
credentials += {
  (sys.env.get("USERNAME"), sys.env.get("PASSWORD")) match {
    case (Some(user: String), Some(pwd: String)) =>
      Credentials("Sonatype Nexus Repository Manager", "nexus.***.io", user, pwd)
    case _ => Credentials(Path.userHome / ".sbt" / ".nexuscredentials")
  }
}
```    

adding the `.nexuscredentials` file under `~/.sbt/` with something like:
```
realm=Sonatype Nexus Repository Manager
host=nexus.mobilityservices.io
user=<ldap_username>
password=<ldap_password>
```

The env variables `USERNAME` and `PASSWORD` are LDAP credentials available in gitlab and usable for our Nexus repo.

#### Environment

This library expects some variables to be defined. It is possible to provide them as env variables or as entries in the conf file (i.e.: `application.conf`)

The env variables are:
```
S3_STREAM_FACTORY_BOOTSTRAP_SERVERS
S3_STREAM_FACTORY_TOPIC_GROUP_ID
S3_STREAM_FACTORY_EVENTS_TOPIC
S3_STREAM_FACTORY_KAFKA_AUTO_OFFSET_RESET_CONFIG
```

the config entries are:
```
kafka.s3-stream-factory.consumer {
    bootstrap-servers = ${S3_STREAM_FACTORY_BOOTSTRAP_SERVERS}
    group-id = ${S3_STREAM_FACTORY_TOPIC_GROUP_ID}
    input-topic = ${S3_STREAM_FACTORY_EVENTS_TOPIC}
    auto-offset-reset-config = ${S3_STREAM_FACTORY_KAFKA_AUTO_OFFSET_RESET_CONFIG}
}
```

This library uses/reads the "Alpakka S3" configuration and the "Akka Streams Kafka" configuration in case they are provided in conf files.


### Contribute

To create a new version of this library:
 - branch develop
 - add code (to run tests locally, execute: `make localTest` )
 - increase the version under `version.sbt` (keeping it `-SNAPSHOT`)
 - merge on `develop` => this will trigger a SNAPSHOT "release"
 
When you are done and you want to release a master version
- merge on `master`
- pull `master` locally
- run: `make release` 

#### Release details

`make release` will run a minio container and use the `sbt-native-packager` + `sbt-relase` plugins to:
- check there are no snapshot dependencies
- inquire the release version
- clean target folder
- run tests
- set the release version
- commit the release version
- create a release tag
- publish to the release repository
- set the next snapshot version (increasing the patch number)
- commit the new version
- push all the changes/tags

