package org.example.s3graph.model.perception.drivedata

import akka.Done
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.stream.alpakka.json.scaladsl.JsonReader
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.testkit.TestKit
import akka.util.ByteString
import better.files.Resource
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.parser.parse
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfigImpl}
import org.apache.kafka.common.serialization.{Serdes, Serializer}
import org.example.s3graph.S3StreamGraphFactory.StartGraph
import org.example.s3graph.model.perception.drivedata.model.DriveDataS3EventValue
import org.example.s3graph.support.{DriveDataS3EventFactory, S3Client}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.mockito.MockitoSugar

import java.io.{ByteArrayInputStream, InputStream}
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Try

class DriveDataS3StreamGraphFactoryTest extends TestKit(ActorSystem("test-system"))
  with WordSpecLike
  with ScalaFutures
  with MockitoSugar
  with EmbeddedKafka
  with Matchers
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with LazyLogging {

  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(60, Seconds), interval = Span(500, Millis))

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  implicit val mat: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(system).withSupervisionStrategy(_ => Supervision.Stop)
  )

  implicit val config: EmbeddedKafkaConfigImpl = EmbeddedKafkaConfigImpl(
    kafkaPort = 6001,
    zooKeeperPort = 6000,
    Map[String, String](),
    Map[String, String](),
    Map[String, String]()
  )

  implicit val serializer: Serializer[String] = Serdes.String.serializer()
  val bucket = "das-perception"
  val inputTopic = "inputTopic"

  override def beforeEach(): Unit = {
    super.beforeAll()
    EmbeddedKafka.start()
    createCustomTopic(inputTopic, Map[String, String](), 10)
    Try(Await.result(S3.makeBucket(bucket), 10.seconds))
  }

  override def afterEach(): Unit = {
    super.afterAll()
    EmbeddedKafka.stop()
  }

  "DriveDataS3StreamGraphFactory" should {

    "fetch and process new published json" in {

      val jsonFilename = "element.json"
      val mode = "dump"
      val date = "2019-01-01"
      val diaryID = "tokenID"
      val vehicleId = "vehicleId"
      val driveId = "driveToken"
      val seqNum = "seqNum"
      val seqToken = "sequenceToken"
      val schemaVersion = 3

      val (s3Key: String, s3Value: String) = createUploadS3Object(jsonFilename, date, diaryID, vehicleId, driveId, seqNum, seqToken, mode, schemaVersion)

      val elementsAsJson: Json = parse(Resource.getAsString(jsonFilename)).right.get

      val assertPromise: Promise[Seq[Assertion]] = Promise[Seq[Assertion]]()
      val assertFuture = assertPromise.future

      val s3BytestringSink: DriveDataS3EventValue => Sink[ByteString, Future[Unit]] = (s3Event: DriveDataS3EventValue) => {
        JsonReader.select(s"""$$.element[*]""")
          .map(_.decodeString("UTF-8"))
          .map { element =>
            Seq(
              assert(elementsAsJson.hcursor.downField("element").as[List[Json]].right.get.contains(parse(element).right.get)),
              assert(s3Event === parse(s3Value).right.get.as[DriveDataS3EventValue].right.get)
            )
          }
          .toMat(Sink.seq)(Keep.right)
          .mapMaterializedValue(_.onComplete(tried => assertPromise.complete(tried.map(_.flatten))))
          .mapMaterializedValue(_ => Future(()))
      }

      val driveDataS3StreamGraph: ActorRef = system.actorOf(
        Props(DriveDataS3StreamGraphFactory(s3BytestringSink))
      )

      val promiseStart: Promise[DrainingControl[Done]] = Promise[DrainingControl[Done]]()
      val futureStartGraph = promiseStart.future

      driveDataS3StreamGraph ! StartGraph(promiseStart)
      Await.result(futureStartGraph, 30.seconds)

      Thread.sleep(1000)

      publishToKafka[String, String](inputTopic, s3Key, s3Value)

      whenReady(assertFuture) {
        case assertions: Seq[Assertion] => assertions.foreach(_ == org.scalatest.Succeeded)
        case other => fail(other.toString)
      }

    }


    "fetch and process new published json, applying filter" in {

      val inputTopic = "inputTopic"

      val jsonFilename = "element.json"
      val jsonFilename2 = "element2.json"

      val date = "2019-01-01"
      val diaryID = "tokenID"
      val vehicleId = "vehicleId"
      val driveId = "driveToken"
      val seqNum = "seqNum"
      val seqToken = "sequenceToken"
      val schemaVersion = 3

      val mode = "dump"
      val mode2 = "master"

      val (s3Key: String, s3Value: String) = createUploadS3Object(jsonFilename, date, diaryID, vehicleId, driveId, seqNum, seqToken, mode, schemaVersion)
      val (s3Key2: String, s3Value2: String) = createUploadS3Object(jsonFilename2, date, diaryID, vehicleId, driveId, seqNum, seqToken, mode2, schemaVersion)

      val assertPromise: Promise[Seq[Assertion]] = Promise[Seq[Assertion]]()
      val assertFuture = assertPromise.future

      val elementsAsJson: Json = parse(Resource.getAsString("element.json")).right.get

      val s3BytestringSinkProvider: DriveDataS3EventValue => Sink[ByteString, Future[Unit]] = (s3Event: DriveDataS3EventValue) => {
        JsonReader.select(s"""$$.element[*]""")
          .map(_.decodeString("UTF-8"))
          .map { element =>
            Seq(
              assert(elementsAsJson.hcursor.downField("element").as[List[Json]].right.get.contains(parse(element).right.get)),
              assert(s3Event === parse(s3Value).right.get.as[DriveDataS3EventValue].right.get),
              assert(s3Event.pathKeys.mode === mode)
            )
          }
          .toMat(Sink.seq)(Keep.right)
          .mapMaterializedValue(_.onComplete(tried => assertPromise.complete(tried.map(_.flatten))))
          .mapMaterializedValue(_ => Future(()))
      }

      val driveDataS3StreamGraph: ActorRef = system.actorOf(
        Props(DriveDataS3StreamGraphFactory(s3BytestringSinkProvider, _.pathKeys.mode == mode))
      )

      val promiseStart: Promise[DrainingControl[Done]] = Promise[DrainingControl[Done]]()
      val futureStartGraph = promiseStart.future

      driveDataS3StreamGraph ! StartGraph(promiseStart)
      Await.result(futureStartGraph, 30.seconds)

      Thread.sleep(1000)

      publishToKafka[String, String](inputTopic, s3Key, s3Value)
      publishToKafka[String, String](inputTopic, s3Key2, s3Value2)

      whenReady(assertFuture) {
        case assertions: Seq[Assertion] =>
          assertions.size should equal(6)
          assertions.foreach(_ should equal(Succeeded))
        case other => fail(other.toString)
      }

    }
  }


  private def createUploadS3Object(jsonFilename: String, date: String, diaryID: String, vehicleId: String, driveId: String, seqNum: String, seqToken: String, mode: String, schemaVersion: Int) = {
    val elementJson: InputStream = Resource.getAsStream(jsonFilename)

    val key = s"perception_data/$mode/drive_data/v$schemaVersion/${date}_$diaryID/vehicle_$vehicleId/drives/$driveId/sequences/${seqNum}_$seqToken/element.avro"

    val (s3Key, s3Value) = DriveDataS3EventFactory.newS3Event(mode, schemaVersion, bucket, key, date, System.currentTimeMillis(), diaryID, vehicleId,
      "table", "element.avro", Some(driveId), Some(seqNum), Some(seqToken))

    val elementJsonBytes = new Array[Byte](elementJson.available())
    elementJson.read(elementJsonBytes)

    Await.result(S3Client.multipartUpload(new ByteArrayInputStream(elementJsonBytes), bucket, key), 5.seconds)
    (s3Key, s3Value)
  }


}
