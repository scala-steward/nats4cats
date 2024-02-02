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

package nats4cats.service

import cats.implicits.*

import cats.effect.kernel.Sync

import nats4cats.service.otel4s.given
import nats4cats.{Deserializer, Message, Nats, Serializer}

import io.nats.client.impl.Headers
import org.typelevel.otel4s.trace.Tracer

import scala.jdk.CollectionConverters.*

trait Client[F[_]] {
  def request[I, O](subject: String, data: I, headers: Headers = Headers())(using Serializer[F, I], Deserializer[F, O]): F[Message[O]]
}

object Client {
  def apply[F[_]](using Client[F]): Client[F] = summon[Client[F]]

  given [F[_]: Sync: Nats: Tracer]: Client[F] with {
    def request[I, O](subject: String, data: I, headers: Headers = Headers())(using Serializer[F, I], Deserializer[F, O]): F[Message[O]] =
      Tracer[F]
        .span("client.request")
        .surround(
          for {
            headers <- Tracer[F].propagate(Headers())
            result <- Nats[F].request[I, O](subject, data, headers).flatMap {
              case msg if msg.headers.containsKey("Nats-Service-Error-Code") =>
                val errorCode    = msg.headers.get("Nats-Service-Error-Code").asScala.headOption.getOrElse("500").toInt
                val errorMessage = msg.headers.get("Nats-Service-Error").asScala.headOption.getOrElse("Unknown error")
                Sync[F].raiseError(new ServiceError(errorCode, errorMessage))
              case msg => msg.pure[F]
            }
          } yield result
        )
  }
}