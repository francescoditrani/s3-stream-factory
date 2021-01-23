package org.example.s3graph.model.perception.s3folder

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import org.example.s3graph.S3EventToTransformerFlow.{BytestringSourcesS3EventToTransformerFlow, S3OptionalSource}
import org.example.s3graph.S3StreamGraphFactory
import org.example.s3graph.model.perception.s3folder.model.S3FolderEventValue

import scala.concurrent.{ExecutionContextExecutor, Future}

case class S3FolderStreamGraphFactory(s3SinkProvider: S3FolderEventValue => Sink[S3OptionalSource, Future[Unit]],
                                      filterPred: S3FolderEventValue => Boolean = _ => true,
                                      parallelism: Int = 1)(
                                       implicit system: ActorSystem, mat: ActorMaterializer, ec: ExecutionContextExecutor)
  extends S3StreamGraphFactory[S3FolderEventValue, S3OptionalSource](s3SinkProvider)(filterPred)(parallelism)