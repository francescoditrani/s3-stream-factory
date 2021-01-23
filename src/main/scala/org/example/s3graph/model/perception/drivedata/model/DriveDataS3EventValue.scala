package org.example.s3graph.model.perception.drivedata.model

import io.circe._
import io.circe.generic.semiauto._
import org.example.s3graph.model.S3BaseEvent


final case class DriveDataS3EventObject(
                                         s3Path: String,
                                         key: String
                                       )

final case class DriveDataS3EventBucket(
                                         name: String,
                                       )

final case class DriveDataS3EventPathKeys(
                                           mode: String,
                                           schemaVersion: Int,
                                           datetime: String,
                                           diaryToken: String,
                                           vehicleId: String,
                                           fileType: String,
                                           filename: String,
                                           driveId: Option[String] = None,
                                           sequenceNumber: Option[String] = None,
                                           sequenceToken: Option[String] = None
                                         )

final case class DriveDataS3EventValue(
                                        `object`: DriveDataS3EventObject,
                                        eventTimeUtc: Long,
                                        pathKeys: DriveDataS3EventPathKeys,
                                        bucket: DriveDataS3EventBucket,
                                      ) extends S3BaseEvent {

  override def bucketName: String = this.bucket.name

  override def s3Key: String = this.`object`.key

}


object DriveDataS3EventValue {

  implicit lazy val objectEncoder: Encoder[DriveDataS3EventObject] = deriveEncoder[DriveDataS3EventObject]
  implicit lazy val valueEncoder: Encoder[DriveDataS3EventValue] = deriveEncoder[DriveDataS3EventValue]
  implicit lazy val pathKeysEncoder: Encoder[DriveDataS3EventPathKeys] = deriveEncoder[DriveDataS3EventPathKeys]
  implicit lazy val bucketEncoder: Encoder[DriveDataS3EventBucket] = deriveEncoder[DriveDataS3EventBucket]

  implicit lazy val objectDecoder: Decoder[DriveDataS3EventObject] = deriveDecoder[DriveDataS3EventObject]
  implicit lazy val pathKeysDecoder: Decoder[DriveDataS3EventPathKeys] = deriveDecoder[DriveDataS3EventPathKeys]
  implicit lazy val bucketDecoder: Decoder[DriveDataS3EventBucket] = deriveDecoder[DriveDataS3EventBucket]


  implicit lazy val valueDecoder: Decoder[DriveDataS3EventValue] = deriveDecoder[DriveDataS3EventValue]

}
