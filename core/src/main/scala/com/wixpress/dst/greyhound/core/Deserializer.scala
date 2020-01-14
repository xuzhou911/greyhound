package com.wixpress.dst.greyhound.core

import org.apache.kafka.common.serialization.{Deserializer => KafkaDeserializer}
import zio.{Chunk, Task}

trait Deserializer[+A] {
  def deserialize(topic: Topic, headers: Headers, data: Chunk[Byte]): Task[A]

  def map[B](f: A => B): Deserializer[B] =
    (topic: Topic, headers: Headers, data: Chunk[Byte]) =>
      deserialize(topic, headers, data).map(f)

  def mapM[B](f: A => Task[B]): Deserializer[B] =
    (topic: Topic, headers: Headers, data: Chunk[Byte]) =>
      deserialize(topic, headers, data).flatMap(f)

  def orElse[A1 >: A](other: => Deserializer[A1]): Deserializer[A1] =
    (topic: Topic, headers: Headers, data: Chunk[Byte]) =>
      deserialize(topic, headers, data) orElse
        other.deserialize(topic, headers, data)
}

object Deserializer {
  def apply[A](deserializer: KafkaDeserializer[A]): Deserializer[A] =
    (topic: Topic, _: Headers, data: Chunk[Byte]) =>
      Task(deserializer.deserialize(topic, data.toArray))

  def apply[A](f: (Topic, Headers, Chunk[Byte]) => Task[A]): Deserializer[A] =
    (topic: Topic, headers: Headers, data: Chunk[Byte]) =>
      f(topic, headers, data)
}