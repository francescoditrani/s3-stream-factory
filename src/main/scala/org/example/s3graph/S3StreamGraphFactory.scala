package org.example.s3graph

import akka.Done
import akka.actor.{Actor, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.kafka.{CommitterSettings, Subscriptions}
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, Attributes}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import org.example.s3graph.S3StreamGraphFactory.{ShutdownAll, StartGraph}
import org.example.s3graph.model.S3BaseEvent
import org.example.s3graph.kafka.{KafkaConsumerConfiguration, S3EventsConsumerSettings}

import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.util.Try

case class S3StreamGraphFactory[S3E <: S3BaseEvent](
                                                s3SinkProvider: S3E => Sink[ByteString, Future[Done]],
                                                s3EventToExecutedGraphFlow: S3EventToExecutedGraphFlow[S3E],
                                                filter: S3E => Boolean = (_: S3E) => true,
                                                parallelism: Int = 1
                                              )(
                                                implicit
                                                system: ActorSystem,
                                                mat: ActorMaterializer,
                                                ec: ExecutionContextExecutor,
                                                decoder: Decoder[S3E]
                                              ) extends Actor with LazyLogging {

  private val committerSettings = CommitterSettings(system)
  implicit private val log: LoggingAdapter = Logging.getLogger(system.eventStream, this)
  private val loggerName = s"${this.getClass.getSimpleName}"
  private val loggerAttributes: Attributes = Attributes.logLevels(
    onElement = Attributes.LogLevels.Debug,
    onFailure = Attributes.LogLevels.Error,
    onFinish = Attributes.LogLevels.Info)

  private val runnableGraph: RunnableGraph[DrainingControl[Done]] =
    Consumer.committableSource(S3EventsConsumerSettings(), Subscriptions.topics(KafkaConsumerConfiguration().inputTopic))
      .via(s3EventToExecutedGraphFlow(s3SinkProvider, filter, parallelism))
      .log(loggerName).addAttributes(loggerAttributes)
      .map(_._1) //TODO we could attach another Sink at this point to send the other result _._2
      .toMat(Committer.sink(committerSettings))(Keep.both)
      .mapMaterializedValue(DrainingControl.apply)

  private var isShuttingDown: Boolean = false

  override def receive: Receive = {
    case StartGraph(promiseResult) =>
      logger.info("Starting graph!")
      val tryRunningGraph = Try(runnableGraph.run())
      tryRunningGraph.map(runningGraph => context.become(running(runningGraph)))
      promiseResult.complete(tryRunningGraph)
  }

  private def running(runningGraph: DrainingControl[Done]): Receive = {
    case ShutdownAll =>
      if (!isShuttingDown) {
        logger.info("Shutting down graph and application!!")
        runningGraph.drainAndShutdown().map(_ => system.terminate())
        isShuttingDown = true
      } else logger.info("Already shutting down!!")
  }

}


object S3StreamGraphFactory {

  case object ShutdownAll

  case class StartGraph(promiseResult: Promise[DrainingControl[Done]])

}