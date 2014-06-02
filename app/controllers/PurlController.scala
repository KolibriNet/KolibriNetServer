package controllers

import play.api.libs.json.{ JsObject, Json }
import traits.ExtendedController
import models._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import helper.ResultHelper._
import play.api.mvc.{ Action, Result }
import scala.Some

/**
 * User: Björn Reimer
 * Date: 1/09/13
 * Time: 4:30 PM
 */

object PurlController extends ExtendedController {

  /**
   * Actions
   */
  def getPurl(id: String, offset: Int = 0, limit: Int = 0) = Action.async {
    request =>
      def externalUserResponse(identity: Identity, purl: Purl): Future[Result] = {
        // check if we need to generate a new token
        val token = identity.tokens.headOption.getOrElse {
          val t = Token.createDefault()
          identity.addToken(t)
          t
        }
        //get conversation
        Conversation.findByMessageId(purl.messageId, limit, offset).flatMap {
          case None => Future(resNotFound("conversation"))
          case Some(conversation) =>
            conversation.hasMemberFutureResult(identity.id) {

              // return result
              conversation.toJsonWithIdentities.map {
                js =>
                  val res: JsObject =
                    Json.obj("conversation" -> js) ++
                      Json.obj("identity" -> identity.toPrivateJson) ++
                      Json.obj("token" -> token.id.toJson)

                  resOK(res)
              }
            }
        }
      }

      def getPurlWithToken(purl: Purl, token: String): Future[Result] = {
        // get identity behind purl
        Identity.find(purl.identityId).flatMap {
          case None => Future(resNotFound("purl"))
          case Some(identity) =>
            // check if the identity has an account
            identity.accountId match {
              case None => externalUserResponse(identity, purl)
              case Some(a) =>
                // purl belongs to a registered user, check we have the right token
                identity.tokens.exists(_.id.id.equals(token)) match {
                  case false => Future(resUnauthorized("This purl belongs to a different identity"))
                  case true =>
                    // get conversation
                    Conversation.findByMessageId(purl.messageId, limit, offset).flatMap {
                      case None => Future(resNotFound("conversation"))
                      case Some(conversation) =>
                        conversation.hasMemberFutureResult(identity.id) {
                          // return result
                          conversation.toJsonWithIdentities.map {
                            js =>
                              val res: JsObject =
                                Json.obj("conversation" -> js) ++
                                  Json.obj("identity" -> identity.toPrivateJson)
                              resOK(res)
                          }
                        }
                    }
                }
            }
        }
      }

      def getPurlWithoutToken(purl: Purl): Future[Result] = {
        // check if identity is an external user
        Identity.find(purl.identityId).flatMap {
          case None => Future(resNotFound("identity"))
          case Some(identity) =>
            identity.accountId match {
              case Some(a) => Future(resUnauthorized("This purl belongs to a registered user, pleas supply token"))
              case None    => externalUserResponse(identity, purl)
            }
        }
      }

      Purl.find(id).flatMap {
        case None => Future(resNotFound("purl"))
        case Some(purl) =>

          // check if we have an authentication header
          request.headers.get("Authorization") match {
            case None        => getPurlWithoutToken(purl)
            case Some(token) => getPurlWithToken(purl, token)
          }
      }
  }
}