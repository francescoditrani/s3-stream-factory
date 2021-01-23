package org.example.s3graph.util

import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.parse
import io.circe.{Decoder, ParsingFailure}
import org.example.s3graph.error.ParsingException

/**
 * Json parser. Expects an implicit Circe decoder. Models the failure as Either Left.
 */
object ParseInto extends LazyLogging {

  def apply[T](jsonString: String)(implicit dec: Decoder[T]): Either[ParsingException, T] = {
    parse(jsonString) match {
      case Right(parsed) =>
        parsed.as[T].left.map { failure =>
          logger.error(s"Circe decoding failure: ${failure.getMessage()}. Original String: $jsonString")
          ParsingException(failure.getMessage())
        }
      case Left(error: ParsingFailure) =>
        logger.error(s"Parsing failure. Error message: ${error.getMessage()}. Original String: $jsonString")
        Left(ParsingException(error.getMessage()))
    }

  }

}