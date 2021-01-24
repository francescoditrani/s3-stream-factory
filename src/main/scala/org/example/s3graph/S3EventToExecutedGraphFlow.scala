package org.example.s3graph

import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset}
import akka.stream.ActorMaterializer
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import org.example.s3graph.error.ParsingException
import org.example.s3graph.kafka.KafkaCommittableFlow
import org.example.s3graph.model.S3BaseEvent
import org.example.s3graph.util.ParseInto

import java.util.NoSuchElementException
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * A class used that models a Flow operator that:
  * - filters incoming events applying the `filter` predicate;
  * - parses incoming events in S3E objects, ignoring parsing errors;
  * - fetches S3 object(s) referenced in the incoming S3E event (using the passed parallelism);
  * - generates a Source for each S3 object;
  * - runs each Source with the Sink generated applying the `s3BytestringSinkProvider` function;
  * - outputs CommittableOffsets in case of successfully ran graph.
  */
case class S3EventToExecutedGraphFlow[S3E <: S3BaseEvent]() extends LazyLogging {

  def apply(
    s3BytestringSinkProvider: S3E => Sink[ByteString, Future[Done]],
    filter: S3E => Boolean,
    parallelism: Int
  )(implicit
    actorSystem: ActorSystem,
    mat: ActorMaterializer,
    ec: ExecutionContextExecutor,
    decoder: Decoder[S3E]
  ): Flow[CommittableMessage[String, String], (CommittableOffset, Option[Done]), NotUsed] = {

    def fetchS3ObjectAndFlowIntoSink(s3Event: S3E): Future[Option[Done]] = {
      val (bucket, key) = (s3Event.bucketName, s3Event.s3Key)

      if (filter(s3Event)) {
        logger.info(s"Fetching file from bucket: $bucket, key: $key")

        S3.download(bucket, key)
          .runWith(Sink.head.mapMaterializedValue(_.recover {
            case ex: NoSuchElementException =>
              val errorMsg =
                s"S3 download returned no elements downloading from bucket: [$bucket], key: [$key]"
              logger.error(errorMsg, ex)
              None
          }))
          .flatMap(_.map {
            case (source, _) =>
              source.runWith(s3BytestringSinkProvider(s3Event)).map(Some(_))
          }.getOrElse(Future(None)))
      } else Future(None)

    }

    KafkaCommittableFlow(
      Flow[CommittableMessage[String, String]]
        .map(message => ParseInto[S3E](message.record.value()))
        .mapAsync(parallelism) {
          case Right(event) => fetchS3ObjectAndFlowIntoSink(event)
          case Left(parsingError: ParsingException) =>
            //TODO extend by providing function to handle parsing errors?
            logger.error("Error handling entities. Ignoring event.", parsingError)
            Future(None)
        }
    )
  }

}
