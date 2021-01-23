package org.example.s3graph

import akka.NotUsed
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset}
import akka.stream.ActorMaterializer
import akka.stream.alpakka.s3.ObjectMetadata
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import org.example.s3graph.error.ParsingException
import org.example.s3graph.model.S3BaseEvent
import org.example.s3graph.kafka.KafkaCommittableFlow
import org.example.s3graph.util.ParseInto

import java.util.NoSuchElementException
import scala.concurrent.{ExecutionContextExecutor, Future}

trait S3EventToTransformerFlow[In] extends LazyLogging {

  def apply[S3E <: S3BaseEvent](s3BytestringSinkProvider: S3E => Sink[In, Future[Unit]],
                                filterPred: S3E => Boolean,
                                parallelism: Int)(
                  implicit actorSystem: ActorSystem, mat: ActorMaterializer, ec: ExecutionContextExecutor, decoder: Decoder[S3E]
                ): Flow[CommittableMessage[String, String], (CommittableOffset, Option[Unit]), NotUsed]

}

object S3EventToTransformerFlow {

  type S3OptionalSource = Option[(Source[ByteString, NotUsed], ObjectMetadata)]

  implicit object BytestringS3EventToTransformerFlow extends S3EventToTransformerFlow[ByteString] {

    override def apply[S3E <: S3BaseEvent](s3BytestringSinkProvider: S3E => Sink[ByteString, Future[Unit]],
                            filterPred: S3E => Boolean,
                            parallelism: Int)(
                             implicit actorSystem: ActorSystem, mat: ActorMaterializer, ec: ExecutionContextExecutor, decoder: Decoder[S3E]
                           ): Flow[CommittableMessage[String, String], (CommittableOffset, Option[Unit]), NotUsed] = {


      def fetchS3ObjectAndApplySink(s3Event: S3E): Future[Option[Unit]] = {
        val (bucket, key) = (s3Event.bucketName, s3Event.s3Key)

        if (filterPred(s3Event)) {
          logger.info(s"Fetching file from bucket: $bucket, key: $key")

          S3.download(bucket, key).runWith(Sink.head.mapMaterializedValue(_.recover {
            case ex: NoSuchElementException =>
              val errorMsg = s"S3 download returned no elements downloading from bucket: [$bucket], key: [$key]"
              logger.error(errorMsg, ex)
              None
          })).flatMap(
            _.map { case (source, _) =>
              source.runWith(s3BytestringSinkProvider(s3Event)).map(Some(_))
            }.getOrElse(Future(None))
          )
        } else Future(None)

      }

      KafkaCommittableFlow(
        Flow[CommittableMessage[String, String]]
          .map(message => ParseInto[S3E](message.record.value()))
          .mapAsync(parallelism) {
            case Right(event) => fetchS3ObjectAndApplySink(event)
            case Left(parsingError: ParsingException) =>
              logger.error("Error handling entities. Ignoring event.", parsingError)
              Future(None)
          })
    }
  }


  implicit object BytestringSourcesS3EventToTransformerFlow extends S3EventToTransformerFlow[S3OptionalSource] {

    override def apply[S3E <: S3BaseEvent](s3SourceBytestringSinkProvider: S3E => Sink[S3OptionalSource, Future[Unit]],
                                           filterPred: S3E => Boolean,
                                           parallelism: Int)(
                             implicit actorSystem: ActorSystem, mat: ActorMaterializer, ec: ExecutionContextExecutor, decoder: Decoder[S3E]
                           ): Flow[CommittableMessage[String, String], (CommittableOffset, Option[Unit]), NotUsed] = {


      def fetchS3ObjectAndApplySink(s3Event: S3E): Future[Option[Unit]] = {
        val (bucket, key) = (s3Event.bucketName, s3Event.s3Key)

        if (filterPred(s3Event)) {
          logger.info(s"Fetching file from bucket: $bucket, key: $key")

          S3.listBucket(bucket, Some(key))
            .map(_.key)
            .map(S3.download(bucket, _))
            .mapAsync(1)(_.runWith(Sink.head.mapMaterializedValue(_.recover {
                case ex: NoSuchElementException =>
                  val errorMsg = s"S3 download returned no elements downloading from bucket: [$bucket], key: [$key]"
                  logger.error(errorMsg, ex)
                  None
              }))
            )
            .runWith(s3SourceBytestringSinkProvider(s3Event)).map(Some(_))
        } else Future(None)

      }

      KafkaCommittableFlow(
        Flow[CommittableMessage[String, String]]
          .map(message => ParseInto[S3E](message.record.value()))
          .mapAsync(parallelism) {
            case Right(event) => fetchS3ObjectAndApplySink(event)
            case Left(parsingError: ParsingException) =>
              logger.error("Error handling entities. Ignoring event.", parsingError)
              Future(None)
          })
    }
  }


}