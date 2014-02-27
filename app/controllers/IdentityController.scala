package controllers

import traits.ExtendedController
import models._
import play.api.mvc.Action
import scala.concurrent.{ Future, ExecutionContext }
import ExecutionContext.Implicits.global
import helper.ResultHelper._
import helper.AuthAction
import scala.Some
import play.api.libs.json._
import scala.Some
import helper.JsonHelper._
import scala.Some

import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._

/**
 * User: Björn Reimer
 * Date: 1/20/14
 * Time: 12:07 PM
 */
object IdentityController extends ExtendedController {

  def getIdentityById(id: String) = Action.async {
    val mongoId = new MongoId(id)

    Identity.find(mongoId).map {
      case None           => resNotFound("identity")
      case Some(identity) => resOK(identity.toPublicJson)
    }
  }

  def getIdentityByToken = AuthAction.async {
    request =>

      val mongoId = request.identity.id

      Identity.find(mongoId).map {
        case None           => resNotFound("identity")
        case Some(identity) => resOK(identity.toPrivateJson)
      }
  }

  def updateIdentity() = AuthAction.async(parse.tolerantJson) {
    request =>
      validateFuture[IdentityUpdate](request.body, IdentityUpdate.reads) {
        identityUpdate =>
          {
            request.identity.update(identityUpdate).map {
              case false => resServerError("nothing updated")
              case true => resOK("updated")


            }
          }
      }
  }

  def search() = Action.async(parse.tolerantJson) {

    request =>

      case class VerifyRequest(cameoId: String)

      def reads: Reads[VerifyRequest] =
        (__ \ 'cameoId).read[String](minLength[String](4)).map {
          l => VerifyRequest(l)
        }

      validateFuture(request.body, reads) {
        vr =>
          Identity.matchCameoId(vr.cameoId).map {
            list => resOK(list.map { i => i.toPublicSummaryJson })
          }
      }
  }

  def addPublicKey() = AuthAction.async(parse.tolerantJson) {
    request =>
      validateFuture(request.body, PublicKey.createReads) {
        publicKey => request.identity.addPublicKey(publicKey).map {
          case false => resServerError("unable to add")
          case true => resOK(publicKey.toJson)
        }
      }
  }

  def editPublicKey(id: String) = AuthAction.async(parse.tolerantJson) {
    request =>
      validateFuture(request.body, PublicKeyUpdate.format) {
        pku => request.identity.editPublicKey(new MongoId(id), pku).map {
          case false => resServerError("not updated")
          case true => resOK("updated")
        }
      }
  }

  def deletePublicKey(id: String) = AuthAction.async {
    request =>
      request.identity.deletePublicKey(new MongoId(id)).map {
        case false => resServerError("unable to delete")
        case true => resOK("deleted")
      }
  }

}
