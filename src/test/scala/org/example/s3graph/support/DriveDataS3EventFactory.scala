package org.example.s3graph.support

object DriveDataS3EventFactory {

  def newS3Event(
                  mode: String,
                  schemaVersion: Int,
                  bucket: String,
                  key: String,
                  datetime: String,
                  timestamp: Long,
                  diarytoken: String,
                  vehicleId: String,
                  filetype: String,
                  filename: String,
                  driveId: Option[String] = None,
                  seq_num: Option[String] = None,
                  seq_token: Option[String] = None
                ): (String, String) = {

    def quoteOrNull(value: Option[String]): String = value.map{x => s""""$x""""}.getOrElse("null")

    (
      s"""{
         |  "mode" : "$mode",
         |  "schemaVersion" : "$schemaVersion",
         |  "bucket" : "$bucket",
         |  "date" : "$datetime",
         |  "diaryToken" : "$diarytoken",
         |  "vehicleId" : "$vehicleId",
         |  "driveId" : ${quoteOrNull(driveId)}
         |}""".stripMargin,
      s"""{
         |  "awsRegion" : "eu-central-1",
         |  "eventName" : "ObjectCreated:Put",
         |  "eventSource" : "aws:s3",
         |  "eventTimeUtc" : $timestamp,
         |  "eventVersion" : "2.1",
         |  "bucket" : {
         |    "name" : "$bucket",
         |    "arn" : "arn:aws:s3:::mybucket",
         |    "ownerPrincipalId" : "XXXXXXXXXXX"
         |  },
         |  "object" : {
         |    "key" : "$key",
         |    "s3Path" : "s3://$bucket/$key",
         |    "size" : 0,
         |    "eTag" : "d41d8cd98f00b204e9800998ecf8427e",
         |    "versionId" : "",
         |    "sequencer" : "005CE25C21A36ACB25"
         |  },
         |  "pathKeys" : {
         |    "mode" : "$mode",
         |    "schemaVersion" : "$schemaVersion",
         |    "datetime" : "$datetime",
         |    "diaryToken" : "$diarytoken",
         |    "vehicleId" : "$vehicleId",
         |    "fileType" : "$filetype",
         |    "filename" : "$filename",
         |    "driveId" : ${quoteOrNull(driveId)},
         |    "sequenceNumber" : ${quoteOrNull(seq_num)},
         |    "sequenceToken" : ${quoteOrNull(seq_token)}
         |  }
         |}""".stripMargin
    )

  }

}
