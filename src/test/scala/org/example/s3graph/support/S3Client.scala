package org.example.s3graph.support

import akka.stream.Materializer
import akka.stream.alpakka.s3.MultipartUploadResult
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Keep, StreamConverters}

import java.io.ByteArrayInputStream
import scala.concurrent.{ExecutionContext, Future}

object S3Client {

  def multipartUpload(inputStream: ByteArrayInputStream, bucket: String, key: String)(implicit
    materializer: Materializer,
    ec: ExecutionContext
  ): Future[Option[MultipartUploadResult]] =
    StreamConverters
      .fromInputStream(() => inputStream, chunkSize = 100000)
      .toMat(S3.multipartUpload(bucket, key))(Keep.right)
      .mapMaterializedValue(_.map(x => Some(x)))
      .run()

}
