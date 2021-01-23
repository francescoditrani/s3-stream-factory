package org.example.s3graph.model.perception.drivedata

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import org.example.s3graph.S3EventToTransformerFlow.BytestringS3EventToTransformerFlow
import org.example.s3graph.S3StreamGraphFactory
import org.example.s3graph.model.perception.drivedata.model.DriveDataS3EventValue

import scala.concurrent.{ExecutionContextExecutor, Future}

case class DriveDataS3StreamGraphFactory(s3SinkProvider: DriveDataS3EventValue => Sink[ByteString, Future[Unit]],
                                         filterPred: DriveDataS3EventValue => Boolean = _ => true,
                                         parallelism: Int = 1)(
                                          implicit system: ActorSystem, mat: ActorMaterializer, ec: ExecutionContextExecutor)
  extends S3StreamGraphFactory[DriveDataS3EventValue, ByteString](s3SinkProvider)(filterPred)(parallelism)
