package com.wixpress.dst.greyhound.core.producer

import java.util.concurrent.TimeUnit

import com.wixpress.dst.greyhound.core.producer.ProducerMetric._
import com.wixpress.dst.greyhound.core.producer.ReportingProducerTest._
import com.wixpress.dst.greyhound.core.testkit.{BaseTest, FakeProducer, TestMetrics}
import zio._
import zio.clock.Clock
import zio.duration._
import zio.test.environment.TestClock

import scala.concurrent.duration.FiniteDuration

class ReportingProducerTest extends BaseTest[Clock with TestClock with TestMetrics] {

  override def env: UManaged[Clock with TestClock with TestMetrics] = {
    for {
      env <- zio.test.environment.testEnvironment.build
      testMetrics <- TestMetrics.make
    } yield env ++ testMetrics
  }

  "delegate to internal producer" in {
    for {
      internal <- FakeProducer.make
      producer = ReportingProducer(internal)
      _ <- producer.produce(record)
      produced <- internal.records.take
    } yield produced must equalTo(record)
  }

  "report metric when producing" in {
    for {
      internal <- FakeProducer.make
      producer = ReportingProducer(internal)
      _ <- producer.produce(record)
      metrics <- TestMetrics.reported
    } yield metrics must contain(ProducingRecord(record))
  }

  "report metric when message is produced successfully" in {
    for {
      promise <- Promise.make[Nothing, Unit]
      metadata = RecordMetadata(topic, partition, 0)
      internal = new Producer[Any] {
        override def produce(record: ProducerRecord[Chunk[Byte], Chunk[Byte]]): IO[ProducerError, RecordMetadata] =
          promise.await.as(metadata)
      }
      producer = ReportingProducer(internal)
      fiber <- producer.produce(record).fork
      _ <- TestClock.adjust(1.second)
      _ <- promise.succeed(()) *> fiber.join
      metrics <- TestMetrics.reported
    } yield metrics must contain(RecordProduced(metadata, FiniteDuration(1, TimeUnit.SECONDS)))
  }

  "report metric when produce fails" in {
    for {
      internal <- FakeProducer.make
      producer = ReportingProducer(internal.failing)
      _ <- producer.produce(record).either
      metrics <- TestMetrics.reported
    } yield metrics must contain(beAnInstanceOf[ProduceFailed])
  }

}

object ReportingProducerTest {
  val topic = "topic"
  val partition = 0
  val record = ProducerRecord(topic, Chunk.empty, partition = Some(partition))
}
