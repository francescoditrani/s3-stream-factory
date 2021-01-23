package org.example.s3graph.kafka

import akka.NotUsed
import akka.kafka.ConsumerMessage
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset}
import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Zip}

class KafkaCommittableFlow

object KafkaCommittableFlow {

  def apply[K, V, O](flow: Flow[CommittableMessage[K, V], O, NotUsed]): Flow[CommittableMessage[K, V], (CommittableOffset, O), NotUsed] =
    Flow.fromGraph[CommittableMessage[K, V], (CommittableOffset, O), NotUsed](GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val committableSplitter = b.add(Broadcast[CommittableMessage[K, V]](2))
      val zip = b.add(Zip[ConsumerMessage.CommittableOffset, O]())

      committableSplitter.out(0).map(_.committableOffset) ~> zip.in0

      committableSplitter.out(1).via(flow) ~> zip.in1

      FlowShape(committableSplitter.in, zip.out)
    })

}
