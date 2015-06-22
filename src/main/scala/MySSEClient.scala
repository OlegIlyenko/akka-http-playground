import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import akka.http.scaladsl.util.FastFuture
import de.heikoseeberger.akkasse._
import scala.concurrent.ExecutionContext

object MyEventStreamUnmarshalling extends EventStreamUnmarshalling

trait EventStreamUnmarshalling {

  private val maxSize = bufferMaxSize

  /**
   * The maximum buffer size for the event Stream parser; 8KiB by default.
   */
  def bufferMaxSize: Int = 8192

  implicit final def fromEntityUnmarshaller: FromEntityUnmarshaller[ServerSentEventSource] = {
    val unmarshaller: FromEntityUnmarshaller[ServerSentEventSource] = Unmarshaller { ec => entity =>
      FastFuture.successful(entity
          .dataBytes
          .transform(() => new MyLineParser(maxSize))
          .transform(() => new MyServerSentEventParser(maxSize)))
    }
    unmarshaller.forContentTypes(MediaTypes.`text/event-stream`)
  }
}

import akka.stream.stage.{ Context, StatefulStage }
import akka.util.ByteString

private object MyLineParser {

  final val CR = '\r'.toByte

  final val LF = '\n'.toByte
}

private final class MyLineParser(maxSize: Int) extends StatefulStage[ByteString, String] {
  import MyLineParser._

  private var buffer = ByteString.empty

  var max = 0
  var count = 0

  override def initial = new State {
    override def onPush(bytes: ByteString, ctx: Context[String]) = {
      buffer ++= bytes

      if (buffer.size > max) max = buffer.size
      count += 1
//      println(s"$count\t$max\t${max / 1024}")
//      if (buffer.size > maxSize)
//        ctx.fail(new IllegalStateException(s"maxSize of $maxSize exceeded!"))
//      else
        emit(lines().iterator, ctx)
    }

    private def lines(): Vector[String] = {
      val (lines, nrOfConsumedBytes, _) = (buffer :+ 0)
          .zipWithIndex
          .sliding(2)
          .collect {
        case Seq((CR, n), (LF, _)) => (n, 2)
        case Seq((CR, n), _)       => (n, 1)
        case Seq((LF, n), _)       => (n, 1)
      }
          .foldLeft((Vector.empty[String], 0, false)) {
        case ((slices, from, false), (until, k)) => (slices :+ buffer.slice(from, until).utf8String, until + k, k == 2)
        case ((slices, _, _), (until, _))        => (slices, until + 1, false)
      }
      buffer = buffer.drop(nrOfConsumedBytes)
      lines
    }
  }
}

import akka.stream.stage.{ Context, PushStage }

private object MyServerSentEventParser {

  private final val LF = "\n"

  private final val Data = "data"

  private final val Event = "event"

  private val linePattern = """([^:]+): ?(.*)""".r

  private def parseServerSentEvent(lines: Seq[String]) = {
    val valuesByField = lines
        .collect { case linePattern(field @ (Data | Event), value) => field -> value }
        .groupBy(_._1)
    val data = valuesByField.getOrElse(Data, Vector.empty).map(_._2).mkString(LF)
    val event = valuesByField.getOrElse(Event, Vector.empty).lastOption.map(_._2)
    ServerSentEvent(data, event)
  }
}

private final class MyServerSentEventParser(maxSize: Int) extends PushStage[String, ServerSentEvent] {
  import MyServerSentEventParser._

  private var lines = Vector.empty[String]

  override def onPush(line: String, ctx: Context[ServerSentEvent]) =
    if (line.nonEmpty) {
      lines :+= line
//      if (lines.map(_.length).sum > maxSize)
//        ctx.fail(new IllegalStateException(s"maxSize of $maxSize exceeded!"))
//      else
        ctx.pull()
    } else {
      val event = parseServerSentEvent(lines)
      lines = Vector.empty
      ctx.push(event)
    }
}
