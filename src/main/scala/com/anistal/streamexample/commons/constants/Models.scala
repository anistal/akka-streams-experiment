package com.anistal.streamexample.commons.constants

import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape.proveShapeOf

import scala.util.{Failure, Success}

class Md5Schema(tag: Tag) extends Table[(String, String)](tag, "md5") {
  def id = column[String]("id", O.PrimaryKey)
  def md5 = column[String]("md5")
  def * = (id, md5)
}

object Main extends App {

  implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global

  val db = Database.forConfig("mongo-event-collector.postgresql")
  val tableMd5 = TableQuery[Md5Schema]

  val setup = DBIO.seq(
    (tableMd5.schema).create
  )

  val result = db.run(setup)

  result.onComplete {
    case Success(value) => value
    case Failure(ex) => ex.printStackTrace
  }

  Thread.sleep(1000L)

}