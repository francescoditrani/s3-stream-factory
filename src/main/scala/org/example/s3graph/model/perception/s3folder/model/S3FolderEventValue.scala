package org.example.s3graph.model.perception.s3folder.model

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.example.s3graph.model.S3BaseEvent

case class S3FolderEventValue(bucketName: String, keyPrefix: String) extends S3BaseEvent {

  override def s3Key: String = keyPrefix

}

object S3FolderEventValue {

  implicit lazy val valueDecoder: Decoder[S3FolderEventValue] = deriveDecoder[S3FolderEventValue]

}