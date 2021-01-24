package org.example.s3graph

import akka.Done
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax._
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfigImpl}
import org.apache.kafka.common.serialization.{Serdes, Serializer}
import org.example.s3graph.S3StreamGraphFactory.{ShutdownAll, StartGraph}
import org.example.s3graph.support.{MyS3Event, S3Client}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpecLike, _}
import org.scalatestplus.mockito.MockitoSugar

import java.io.ByteArrayInputStream
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, _}
import scala.util.Try

class S3StreamGraphFactoryTest extends TestKit(ActorSystem("test-system"))
  with ImplicitSender
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
  val bucket = "bucket"
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

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "S3StreamGraphFactory" should {

    "fetch and process three new published json" in {

      val s3ObjectsToEvents = Map(
        "object1" -> MyS3Event(bucket, s3Key = "key"),
        "object2" -> MyS3Event(bucket, s3Key = "key2"),
        "object3" -> MyS3Event(bucket, s3Key = "key3")
      )

      s3ObjectsToEvents.foreach{ case (objectContent, s3EventValue) =>
        Await.result(
          S3Client.multipartUpload(
            new ByteArrayInputStream(objectContent.getBytes),
            s3EventValue.bucketName,
            s3EventValue.s3Key
        ), 5.seconds)
      }

      val s3BytestringSinkProvider: MyS3Event => Sink[ByteString, Future[Done]] = (s3Event: MyS3Event) => {
        Flow[ByteString]
          .map(_.decodeString("UTF-8"))
          .to(Sink.foreach{ content => testActor ! (content -> s3Event)})
          .mapMaterializedValue(_ => Future(Done))
      }

      val s3StreamGraph: ActorRef = system.actorOf(
        Props(S3StreamGraphFactory[MyS3Event](
          s3BytestringSinkProvider,
          S3EventToExecutedGraphFlow[MyS3Event]()
        ))
      )

      val promiseStart: Promise[DrainingControl[Done]] = Promise[DrainingControl[Done]]()
      val futureStartGraph = promiseStart.future

      s3StreamGraph ! StartGraph(promiseStart)
      val graph: DrainingControl[Done] = Await.result(futureStartGraph, 3.seconds)

      Thread.sleep(1000)

      s3ObjectsToEvents.foreach{ case (_, s3Event) =>
        publishToKafka[String, String](inputTopic, s3Event.bucketName, s3Event.asJson.toString())
      }

      val expectedS3ContentsToEvents = receiveN(3, 5.second)
        .map( msg => msg.asInstanceOf[(String, MyS3Event)] )
        .toMap

      assert(s3ObjectsToEvents == expectedS3ContentsToEvents)

      s3StreamGraph ! ShutdownAll

      whenReady(graph.isShutdown) {
        case Done => println("Graph successfully shutdown!")
        case _ => fail()
      }

    }


  }

}
