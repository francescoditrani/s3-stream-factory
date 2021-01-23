package org.example.s3graph.kafka

import akka.actor.ActorSystem
import akka.kafka.ConsumerSettings
import com.typesafe.config.Config
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer


case class S3EventsConsumerSettings()(implicit system: ActorSystem)  {

  private val config: Config = system.settings.config.getConfig("akka.kafka.consumer")
  private val kafkaConf: KafkaConsumerConfiguration = KafkaConsumerConfiguration()

  val consumerSettings: ConsumerSettings[String, String] =
    ConsumerSettings(config, new StringDeserializer(), new StringDeserializer())
      .withBootstrapServers(kafkaConf.bootstrapServers)
      .withGroupId(kafkaConf.groupId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaConf.autoOffsetResetConfig)

}

object S3EventsConsumerSettings {

  def apply()(implicit system: ActorSystem): ConsumerSettings[String, String] =
    new S3EventsConsumerSettings()(system).consumerSettings

}
