import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.unmarshalling.Unmarshal

import language._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Source
import de.heikoseeberger.akkasse.EventStreamMarshalling._
import de.heikoseeberger.akkasse._
import akka.http.scaladsl.client.RequestBuilding.Get

import scala.util.{Success, Failure}

import MyEventStreamUnmarshalling._

object ClientAndServer extends App {
  def toSse(i: Int): ServerSentEvent =
    ServerSentEvent("" + i, "foo")

  def fromSse(sse: ServerSentEvent): Int =
    sse.data.toInt

  def server(): Unit = {
    implicit val system = ActorSystem("server")
    implicit val materializer = ActorFlowMaterializer()
    import system.dispatcher

    var c = 0
    var t = System.currentTimeMillis

    def count(): Unit = {
      c = c + 1
      if (System.currentTimeMillis - t > 1000) {
        println(s"Generated $c times")
        c = 0
        t = System.currentTimeMillis()
      }
    }

    val route: Route =
      (path("numbers") & get) {
        complete(Source(() => Iterator.from(1) map {x => count(); /*println(s"Preparing $x");*/ x} map toSse))
      }

    Http().bindAndHandle(route, "0.0.0.0", 8080)
  }

  def client(): Unit = {
    implicit val system = ActorSystem("client")
    implicit val materializer = ActorFlowMaterializer()
    import system.dispatcher

    Http() singleRequest Get("http://localhost:8080/numbers") flatMap (Unmarshal(_).to[ServerSentEventSource]) onComplete {
      case Success(source) =>
        source.map(fromSse)/*.conflate(_ => 0){case (acc, e) => acc + 1}*/ map {x => /*Thread.sleep(100);*/ x} runForeach (i => ()/*println(s"Got $i")*/) onComplete {
          case Success(_) => println("Finished?")
          case Failure(e) =>
            println("Client:")
            e.printStackTrace()
            system.shutdown()
        }

      case Failure(e) =>
        e.printStackTrace()
        system.shutdown()
    }
  }

  server()
  client()
}
