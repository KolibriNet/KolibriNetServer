package controllers

import helper.CmActions.AuthAction
import helper.ResultHelper._
import models.{ EventSubscription, MongoId }
import play.api.Play
import play.api.Play.current
import play.api.mvc.Controller

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * User: Björn Reimer
 * Date: 09.05.14
 * Time: 13:42
 */
object EventController extends Controller {

  def newSubscription() = AuthAction(allowExternal = true).async(parse.tolerantJson) {
    request =>
      // check if a secret is used to disable max subscription
      val limitEnabled: Boolean = Play.configuration.getString("events.subscription.debug.secret") match {
        case None             => true
        case Some("disabled") => true
        case Some(str) =>
          // check if there is a secret in the body
          (request.body \ "secret").asOpt[String] match {
            case Some(secret) if secret.equals(str) => false
            case _                                  => true
          }
      }

      // check if maximum number for this user is exceeded
      val max = Play.configuration.getInt("events.subscription.user.limit").get
      EventSubscription.countUserSubscriptions(request.identity.id).map {
        case i if limitEnabled && i >= max =>
          resBadRequest("max number of subscription reached")
        case _ =>
          val subscription = EventSubscription.create(request.identity.id)
          EventSubscription.col.insert(subscription)
          resOk(subscription.toJson)
      }
  }

  def getSubscription(id: String) = AuthAction(allowExternal = true).async {
    request =>
      EventSubscription.findAndClear(MongoId(id)).map {
        case None => resNotFound("subscription id")
        case Some(subscription) =>
          resOk(subscription.toJson)
      }
  }
}
