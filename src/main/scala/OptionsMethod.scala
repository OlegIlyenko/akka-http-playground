import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._

object OptionsMethod extends App  {
  implicit val system = ActorSystem("my-options")
  implicit val materializer = ActorFlowMaterializer()

  implicit def rejectionHandler =
    RejectionHandler.newBuilder().handleAll[MethodRejection] { rejections =>
      val methods = rejections map (_.supported)
      lazy val names = methods map (_.name) mkString ", "

      respondWithHeader(Allow(methods)) {
        options {
          complete(s"Supported methods : $names.")
        } ~
        complete(MethodNotAllowed, s"HTTP method not allowed, supported methods: $names!")
      }
    }
    .result()

  val route: Route =
    pathPrefix("orders") {
      pathEnd {
        get {
          complete("Order 1, Order 2")
        } ~
        post {
          complete("Order saved")
        }
      } ~
      path(LongNumber) { id =>
        put {
          complete(s"Order $id")
        }
      }
    }

  Http().bindAndHandle(route, "0.0.0.0", 8080)
}