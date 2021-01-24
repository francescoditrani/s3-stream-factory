package org.example.s3graph.support

import io.circe._
import io.circe.generic.semiauto._
import org.example.s3graph.model.S3BaseEvent


final case class MyS3Event(bucketName: String,
                           s3Key: String) extends S3BaseEvent


object MyS3Event {

  implicit lazy val valueDecoder: Decoder[MyS3Event] = deriveDecoder[MyS3Event]
  implicit lazy val valueEncoder: Encoder[MyS3Event] = deriveEncoder[MyS3Event]

}
