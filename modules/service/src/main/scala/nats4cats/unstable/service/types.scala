/*
 * Copyright 2023 ThatScalaGuy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nats4cats.unstable.service

import cats.effect.kernel.Async

import nats4cats.{Deserializer, Serializer}

import io.nats.client.impl.Headers
import io.nats.service.Group

trait Extension {
  def applyTo[F[_], I, O](endpoint: Endpoint[F[_], I, O])(using Async[F], Deserializer[F, I], Serializer[F, O]): Endpoint[F[_], I, O]
}

final case class Request[A](val data: A, val headers: Headers, val subjectSegments: List[String]) {
  def headerOpt(name: String): Option[String] = Option(headers.getFirst(name))
}

object Request {
  import scala.util.matching.Regex

  def extractFromSubject(input: String, pattern: String): List[String] = {
    val modifiedPattern     = pattern.replaceAll("\\.", "\\\\.").replaceAll("\\*", "(.*?)")
    val regexPattern: Regex = modifiedPattern.r
    regexPattern.findAllIn(input).matchData.flatMap(m => (1 to m.groupCount).map(m.group)).toList
  }
}
final case class GroupList(val groups: List[String]) {
  def toGroup: Option[Group] = groups match {
    case Nil  => None
    case list => Some(groups.map(name => new Group(name)).reduce((a, b) => a.appendGroup(b)))
  }
}

class ServiceError(val code: Int, val message: String) extends RuntimeException(s"$code - $message")

final class InternalServerError extends ServiceError(500, "Internal Server Error")

final class VerboseInternalServerError(cause: Throwable) extends ServiceError(500, s"Internal Server Error - ${cause.getLocalizedMessage()}")
