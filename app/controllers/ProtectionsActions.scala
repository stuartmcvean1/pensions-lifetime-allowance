/*
 * Copyright 2016 HM Revenue & Customs
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

package controllers

import connectors._
import play.api.Logger
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

object ProtectionsActions extends ProtectionsActions{
  override lazy val citizenDetailsConnector = CitizenDetailsConnector
}

trait ProtectionsActions{
  val citizenDetailsConnector: CitizenDetailsConnector

  case class WithCitizenRecordCheckAction(nino: String)(implicit ec: ExecutionContext) extends ActionBuilder[Request] {

    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      implicit val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers)

      def logErrorAndRespond(err: String, status: Status): Future[Result] = {
        Logger.error(err)
        Future.successful(status)
      }
      citizenDetailsConnector.checkCitizenRecord(nino) flatMap { citizenCheckResult =>
        citizenCheckResult match {
          case CitizenRecordOK                  => block(request)
          case CitizenRecordNotFound            => logErrorAndRespond(s"Citizen record not found for '$nino'", NotFound)
          case CitizenRecordLocked              => logErrorAndRespond(s"Citizen record locked for '$nino'", Locked)
          case CitizenRecordOther4xxResponse(e) => logErrorAndRespond(s"Citizen record 4xx response for '$nino'", BadRequest)
          case CitizenRecord5xxResponse(e) if e.upstreamResponseCode == 500   => logErrorAndRespond(s"Citizen record 500 response for '$nino'", InternalServerError)
          case CitizenRecord5xxResponse(e) if e.upstreamResponseCode == 503   => logErrorAndRespond(s"Citizen record 503 response for '$nino'", GatewayTimeout)
          case _ => Future.successful(InternalServerError)
        }
      }
    }
  }
}
