package com.softwaremill.bootzooka.huts
import java.io.File
import java.util.concurrent.Executors

import cats.data.NonEmptyList
import cats.implicits._
import cats.effect.{Blocker, IO}
import com.softwaremill.bootzooka.http.Http
import monix.eval.Task
import com.softwaremill.bootzooka.infrastructure.Json._
import com.softwaremill.bootzooka.util.ServerEndpoints
import com.typesafe.scalalogging.StrictLogging
import sttp.client._
import io.circe.syntax._
import com.softwaremill.bootzooka.util.SttpUtils._
import fs2.text
import monix.execution.ExecutionModel.AlwaysAsyncExecution
import monix.execution.Scheduler
import monix.execution.schedulers.AsyncScheduler
import org.http4s.multipart.Multipart
import monix.nio.text.UTF8Codec._
import monix.nio.file._

import scala.concurrent.ExecutionContext
import monix.execution.Scheduler.Implicits.global
//import org.http4s.multipart.Multipart
//import sttp.tapir.Codec._
/**
  * Created by Ilya Volynin on 16.12.2019 at 12:12.
  */
case class HutsApi(http: Http, config: HutsConfig)(implicit sttpBackend: SttpBackend[Task, Nothing, Nothing]) extends StrictLogging {
  import http._
  import HutsApi._

  private val HutsPath = "huts"

  private val samplesEndpoint = baseEndpoint.post
    .in(HutsPath / "samples")
    .in(jsonBody[Samples_IN])
    .out(jsonBody[Samples_OUT])
    .serverLogic[Task] { data =>
    (for {
      r <- basicRequest.post(uri"${config.url}")
        .body(Samples_Body_Call(data.id).asJson.toString())
        .send()
        .flatMap(handleRemoteResponse[List[HutWithId]])
    } yield Samples_OUT(r)).toOut
  }

  private val readFileBlocker = Executors.newFixedThreadPool(4)

  lazy val scheduler = Scheduler(readFileBlocker, AlwaysAsyncExecution)

  private val fileUploadEndpoint = baseEndpoint.post
    .in(HutsPath / "fu")
    .in(multipartBody[HutBook])
    .out(jsonBody[HutBook_OUT])
    .serverLogic[Task] {
    hb =>
      (for {
        from <- Task.now(java.nio.file.Paths.get(hb.file.getAbsolutePath))
        content <- readAsync(from, 30)(scheduler).pipeThrough(utf8Decode).foldL
      } yield HutBook_OUT(s"$content")).toOut
  }

  val endpoints: ServerEndpoints =
    NonEmptyList
      .of(
        samplesEndpoint,
        fileUploadEndpoint
      )
      .map(_.tag("huts"))

  def bodyParse(m: Multipart[IO]): String = {
    m.parts.find(_.name == Some("dataFile")) match {
      case None => s"Not file"
      case Some(part) =>
        s"""Multipart Data\nParts:${
          m.parts.length
        }
           |File contents: ${
          part.body.through(text.utf8Decode).compile.foldMonoid.unsafeRunSync()
        }""".
          stripMargin
    }
  }
}

object HutsApi {

  case class Samples_IN(id: String)

  case class Samples_OUT(ids: List[HutWithId])

  case class Samples_Body_Call(id: String)

  case class HutWithId(id: String, name: String)

  case class Samples_Body_Response(huts: List[HutWithId])

  case class HutBook(title: String, file: File)

  case class HutBook_OUT(result: String)

}
