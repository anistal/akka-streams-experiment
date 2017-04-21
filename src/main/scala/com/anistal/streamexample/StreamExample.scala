package com.anistal.streamexample

import java.security.MessageDigest
import java.util.UUID

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink}
import com.anistal.streamexample.commons.constants.Md5Schema
import com.anistal.streamexample.commons.logging.AppLogging
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.common.serialization.StringDeserializer
import redis.RedisClient
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.Await
import scala.concurrent.duration._


object StreamExample extends App with AppLogging {

  implicit val system = ActorSystem("stream-example")

  val db = Database.forConfig("mongo-event-collector.postgresql")
  val tableMd5 = TableQuery[Md5Schema]

  val redis = RedisClient()

  val decider: Supervision.Decider = { exception =>
    log.error(exception.getLocalizedMessage, exception)
    Supervision.Resume
  }

  val materializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit val materializer = ActorMaterializer(materializerSettings)
  implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global

  val bootstrapServer = "localhost:9092"
  log.info(bootstrapServer)

  val messageDigestInstance = MessageDigest.getInstance("MD5")
  val consumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(bootstrapServer)
    .withGroupId("group")
    .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")

  //.withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  def append(x: Any): Unit = {
    println(x)
    scala.tools.nsc.io.File("/tmp/1million-md5.out").appendAll(s"${x.toString}\n")
  }

  def writeToDatabase(x: Any): Unit = {
    def uuid = UUID.randomUUID().toString
    def md5 = x.toString

    val result = for {
      _ <- db.run(DBIO.seq(tableMd5 ++= Seq((uuid, md5))))
      _ <- redis.set(uuid, md5)
    } yield { }

    result onFailure {
      case ex => ex.printStackTrace
    }

    Await.result(result, 1 second)
  }

  val calculateMD5 = Flow[ConsumerRecord[String, String]].map { event =>
    val m = java.security.MessageDigest.getInstance("MD5")
    val b = event.value().getBytes("UTF-8")
    m.update(b, 0, b.length)
    new java.math.BigInteger(1, m.digest()).toString(16)
  }

  val calculateMD5String = Flow[String].map { event =>
    val m = java.security.MessageDigest.getInstance("MD5")
    val b = event.getBytes("UTF-8")
    m.update(b, 0, b.length)
    new java.math.BigInteger(1, m.digest()).toString(16)
  }

  // @formatter:off
  val g = RunnableGraph.fromGraph(GraphDSL.create() {
    implicit builder =>
      import GraphDSL.Implicits._

      val A: Outlet[ConsumerRecord[String, String]] =
        builder.add(Consumer.plainSource(consumerSettings, Subscriptions.topics("bookings"))).out
      // Flows
      val B: FlowShape[ConsumerRecord[String, String], String] = builder.add(calculateMD5.async)
      val C: FlowShape[String, String] = builder.add(calculateMD5String.async)
      val D: FlowShape[String, String] = builder.add(calculateMD5String.async)

      // Sinks
      //val E: Inlet[Any] = builder.add(Sink.foreach(append)).in
      val F: Inlet[Any] = builder.add(Sink.foreachParallel(10)(writeToDatabase)).in

      // Graph
      A ~> B ~> C ~> D
      //E <~ D
      F <~ D

      ClosedShape
  })
  // @formatter:on

  g.run()

}
