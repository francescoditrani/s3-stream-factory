NOTE: The migration of this project to open source is a WIP.

The release process to a public repository is not implemented.

# S3 Stream Factory

This library provides a factory to generate an Akka Streams graph that will listen to a configured Kafka topic for events referencing new S3 objects.
Once a new S3 event is published to Kafka, the graph will fetch and stream the referenced object to the provided Sink and will handle Kafka committing.

It's possible to provide a predicate to filter S3 events.
It's possible to configure the processing parallelism.

Currently, S3 event parsing errors and S3 download errors are only logged.

The graph is parametrised for "s3 event type". 
 
![](doc/s3%20stream%20factory.png)

## Usage

```scala

 // define the Sink Provider
  val mySinkProvider: MyS3Event => Sink[ByteString, NotUsed] =
        (s3Event: MyS3Event) => {
          Flow[ByteString]
            .map(_.decodeString("UTF-8"))
            .to(Sink.foreach { content => println(s"s3Event: $s3Event. content: $content") })
        }

  //instantiate the Graph Factory passing the Sink Provider
  val s3StreamGraph: ActorRef = system.actorOf(
    Props(S3StreamGraphFactory(
      s3SinkProvider = mySinkProvider,
      filter = _.s3Key.endsWith(".json"),
      parallelism = 3
    ))
  )

  //start the graph. The promise is completed with the DrainingControl, useful in the shutdown phase.
  val promiseStart = Promise[DrainingControl[Done]]()
  s3StreamGraph ! StartGraph(promiseStart)

  //shutdown the graph
  s3StreamGraph ! ShutdownAll
 
  //wait for the graph shutdown
  Await.result(promiseStart.future.map(_.isShutdown), 5.seconds)


```



##  Dependencies

This library is actually compiled against scala 2.12

These are other versions used for the main dependencies (in case of different versions in the host application, could be necessary to shade these):
```
Confluent platform = "5.1.2"
Akka = "2.5.23"
Akka Streams Kafka = "1.0.4"
Circe = "0.11.1"
Alpakka S3" = "1.0.1"
```

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

This library uses/reads the "Alpakka S3" and the "Akka Streams Kafka" configurations, in case they are provided in conf files.

