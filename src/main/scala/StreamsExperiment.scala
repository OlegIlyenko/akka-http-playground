import language._

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.{Shape, Graph, OperationAttributes, ActorFlowMaterializer}
import akka.stream.scaladsl._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import FlowGraph.Implicits._
import Helper._
import scala.concurrent.duration._

import scala.util.{Failure, Success}

object StreamsExperiment1 extends App {
  println("------------------------")

  implicit val system = ActorSystem("my-test-1")
  implicit val materializer = ActorFlowMaterializer()

  case class Page(url: String, body: String, rank: Int)

  val urls = Source(() => Iterator("a", "b", "c"))

  val foldSink = Sink.fold[Int, Page](0) {case (acc, p) => acc + 1}
  val foldSink1 = Sink.fold[Int, String](0) {case (acc, p) => acc + 1}
  val printSink = Sink.foreach(println)

  val graph = FlowGraph.closed(foldSink) { implicit b => sink =>
    val T = b.add(Broadcast[String](2))
    val Y = b.add(ZipWith[(String, String), Int, Page]((urlAndBody, rank) => Page(urlAndBody._1, urlAndBody._2, rank)))

    urls ~> T.in

    T.out(0) ~> Flow[String].mapAsync(10)(url => Future(url -> (url * 20))) ~> Y.in0
    T.out(1) ~> Flow[String].map(_ => (math.random * 10).toInt) ~> Y.in1

    def log[T] = Flow[T].log("Elem").withAttributes(OperationAttributes.logLevels(onElement = Logging.InfoLevel))

    Y.out ~> Flow[Page].filter(_.url != "b") ~> log[Page] ~> sink
  }

  val foo = graph.run().andShutdown()
}

object StreamEx2 extends App {
  println("------------------------")

  implicit val system = ActorSystem("my-test-1")
  implicit val materializer = ActorFlowMaterializer()

  val nums = Source(() => Iterator.continually{1}.scanLeft(0L){case (acc, elem) => acc + elem})

  val compressed = nums.conflate(_ => 1L){case (acc, _) => acc + 1}

  val timer = Source(1 second, 1 second, ())

  val graph = FlowGraph.closed(Sink.foreach(println)) { implicit  b => sink =>
    val zip = b.add(ZipWith[Long, Unit, Long]((n, _) => n).noBuffer)

    timer ~> zip.in1
    compressed ~> zip.in0

    zip.out.take(10) ~> sink
  }

  graph.run().andShutdown()
}

object StreamEx3 extends App {
  println("------------------------")

  implicit val system = ActorSystem("my-test-1")
  implicit val materializer = ActorFlowMaterializer()

  val nums = Source(() => Iterator.continually{Thread.sleep(500); 1}.scanLeft(0L){case (acc, elem) => acc + elem})

  val foo = nums.map(x => Source(x :: x :: Nil)).flatten(FlattenStrategy.concat)

  foo.runForeach(l => println(l)).andShutdown()
}


object Helper {
  def andShutdown(fut: Future[Any])(implicit sys: ActorSystem) = {
    fut.onComplete { (res) =>
      res match {
        case Failure(error) => error.printStackTrace()
        case Success(n) =>
          println(s"Processed $n")
      }

      sys.shutdown()
    }
  }

  implicit class FutHelper[T](val future: Future[T]) extends AnyVal {
    def andShutdown()(implicit sys: ActorSystem) = Helper.andShutdown(future)(sys)
  }

  implicit class SourceHelper[Out, Mat](val s: Source[Out, Mat]) extends AnyVal {
    def noBuffer = s.withAttributes(OperationAttributes.inputBuffer(initial = 1, max = 1))
  }

  implicit class GraphHelper[S <: Shape, Mat](val g: Graph[S, Mat]) extends AnyVal {
    def noBuffer = g.withAttributes(OperationAttributes.inputBuffer(initial = 1, max = 1))
  }
}


