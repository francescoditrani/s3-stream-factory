package org.example.s3graph.model

trait S3BaseEvent {

  def bucketName: String

  def s3Key: String

}
