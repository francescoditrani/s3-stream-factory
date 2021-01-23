package org.example.s3graph.error

case class ParsingException(message: String) extends LoaderException(message)
