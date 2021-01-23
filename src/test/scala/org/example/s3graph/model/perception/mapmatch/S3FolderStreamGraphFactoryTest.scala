package org.example.s3graph.model.perception.mapmatch

import akka.Done
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.testkit.TestKit
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfigImpl}
import org.apache.kafka.common.serialization.{Serdes, Serializer}
import org.example.s3graph.S3EventToTransformerFlow.S3OptionalSource
import org.example.s3graph.S3StreamGraphFactory.StartGraph
import org.example.s3graph.model.perception.s3folder.S3FolderStreamGraphFactory
import org.example.s3graph.model.perception.s3folder.model.S3FolderEventValue
import org.example.s3graph.support.S3Client
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.mockito.MockitoSugar

import java.io.ByteArrayInputStream
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future, Promise}
import scala.util.Try

class S3FolderStreamGraphFactoryTest extends TestKit(ActorSystem("test-system"))
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
  val bucket = "mapmatched"
  val inputTopic = "inputTopic"

  override def beforeEach(): Unit = {
    super.beforeAll()
    EmbeddedKafka.start()
    Try {
      createCustomTopic(inputTopic, Map[String, String](), 10)
    }
    Try(Await.result(S3.makeBucket(bucket), 10.seconds))
  }

  override def afterEach(): Unit = {
    super.afterAll()
    EmbeddedKafka.stop()
  }

  "MapMatchedAggrS3StreamGraphFactory" should {

    "fetch and process new published json" in {

      val assertPromise: Promise[Assertion] = Promise[Assertion]()
      val assertFuture = assertPromise.future

      val keyPrefix = "key1+2/partition=something"
      val (key1, content1) = (s"$keyPrefix/one", "content1")
      val (key2, content2) = (s"$keyPrefix/suffix", "content2")


      val s3Objects = Seq(S3Object(key1, content1), S3Object(key2, content2))
      uploadS3Objects(s3Objects)

      val s3SinkProvider: S3FolderEventValue => Sink[S3OptionalSource, Future[Unit]] = (s3Event: S3FolderEventValue) => {
        Flow[S3OptionalSource]
          .mapAsync(1) {
            case Some((source, _)) =>
              source
                .runWith(Sink.fold(ByteString.empty)(_.concat(_)))
                .map(_.utf8String)
            case None => fail()
          }.toMat(Sink.seq)(Keep.right)

          .mapMaterializedValue(_.map { contents =>
            assert(contents.toSet === Set(content1, content2))
          })
          .mapMaterializedValue(_.onComplete(triedAssertion => assertPromise.complete(triedAssertion)))
          .mapMaterializedValue(_ => Future(()))
      }

      val s3FolderStreamGraph: ActorRef = system.actorOf(
        Props(S3FolderStreamGraphFactory(s3SinkProvider))
      )

      val promiseStart: Promise[DrainingControl[Done]] = Promise[DrainingControl[Done]]()
      val futureStartGraph = promiseStart.future

      s3FolderStreamGraph ! StartGraph(promiseStart)
      Await.result(futureStartGraph, 30.seconds)

      Thread.sleep(1000)

      publishToKafka[String, String](inputTopic, keyPrefix,
        S3FolderEventValue(bucket, keyPrefix).asJson(deriveEncoder[S3FolderEventValue]).toString()
      )

      whenReady(assertFuture) {
        case assertion: Assertion => assertion should equal(Succeeded)
        case other => fail(other.toString)
      }

    }

  }


  case class S3Object(key: String, content: String)


  private def uploadS3Objects(s3Objects: Seq[S3Object]): Unit = {

    s3Objects.foreach { s3Object =>
      Await.result(
        S3Client.multipartUpload(new ByteArrayInputStream(s3Object.content.getBytes()), bucket, s3Object.key),
        5.seconds
      )
    }

  }

}
