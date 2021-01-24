package org.example.s3graph.kafka

import pureconfig.loadConfigOrThrow

case class KafkaConsumerConfiguration(
  bootstrapServers: String,
  inputTopic: String,
  groupId: String,
  autoOffsetResetConfig: String
)

object KafkaConsumerConfiguration {

  def apply(): KafkaConsumerConfiguration =
    loadConfigOrThrow[KafkaConsumerConfiguration]("kafka.s3-stream-factory.consumer")

}
