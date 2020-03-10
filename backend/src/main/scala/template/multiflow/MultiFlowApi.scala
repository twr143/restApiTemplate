package template.multiflow
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import sttp.client.SttpBackend
import template.http.Http
import cats.data.{Kleisli, NonEmptyList, OptionT}
import template.util.ServerEndpoints
import template.infrastructure.Json._
import cats.implicits._
import io.circe.Codec
import io.circe.generic.AutoDerivation
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import sttp.tapir.Schema
import sttp.tapir.json.circe._

/**
  * Created by Ilya Volynin on 10.03.2020 at 10:54.
  */
case class MultiFlowApi(http: Http)(implicit sttpBackend: SttpBackend[Task, Nothing, Nothing]) extends StrictLogging {
  import MultiFlowApi._
  import http._
  private val mfPath = "mf"

  val multiFlowStringK: Kleisli[OptionT[Task, *], MFRequest, MFResponse] = Kleisli {
    case MFRequestStr(s) => OptionT.liftF(Task.now(MFResponseString(s)))
    case _               => OptionT.none
  }
  val multiFlowIntK: Kleisli[OptionT[Task, *], MFRequest, MFResponse] = Kleisli {
    case MFRequestInt(s) => OptionT.liftF(Task.now(MFResponseInt(s)))
    case _               => OptionT.none
  }
  def optionFlat: OptionT[Task, MFResponse] => Task[MFResponse] = _.getOrElse(MFResponseDefault())
  private val multiFlowEndpoint = baseEndpoint.post
    .in(mfPath / "mfep")
    .in(jsonBody[MFRequest])
    .out(jsonBody[MFResponse])
    .serverLogic[Task](multiFlowStringK <+> multiFlowIntK mapF optionFlat mapF toOutF run)

  val endpoints: ServerEndpoints =
    NonEmptyList
      .of(multiFlowEndpoint)
      .map(_.tag("mf"))

}
object MultiFlowApi extends AutoDerivation {
  implicit val configuration: Configuration = Configuration.default.withDiscriminator("type")

  sealed trait MFRequest
  case class MFRequestStr(s: String) extends MFRequest
  case class MFRequestInt(s: Int) extends MFRequest

  implicit val codecMFRequest: Codec[MFRequest] = deriveConfiguredCodec
  sealed trait MFResponse
  case class MFResponseString(s: String, desc: String = "str resp") extends MFResponse
  case class MFResponseInt(i: Int, desc: String = "int resp") extends MFResponse
  case class MFResponseDefault(desc: String = "default resp") extends MFResponse

}
