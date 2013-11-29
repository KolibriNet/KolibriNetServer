package controllers

import traits.ExtendedController
import reactivemongo.bson._
import scala.concurrent.Future
import play.api.libs.json._
import reactivemongo.api.gridfs.GridFS
import helper.{AuthAction, IdHelper}
import play.api.Logger
import models.{Asset, Message}
import scala.Some
import reactivemongo.api.gridfs.Implicits.DefaultReadFileReader
import java.util.Date
import reactivemongo.core.commands.LastError
import services.Authentication.UserClass
import services.Authentication
import play.api.mvc.Action
import play.api.libs.concurrent.Execution.Implicits._

/**
 * User: Björn Reimer
 * Date: 5/24/13
 * Time: 9:40 PM
 */
object AssetController extends ExtendedController {

  val gridFS = new GridFS(db)
  gridFS.ensureIndex()


  def uploadAsset(token: String, messageId: String) = AuthAction.async(gridFSBodyParser(gridFS)) {
    implicit request =>
      val userClass: UserClass = Authentication.getUserClass(request.token.userClass.getOrElse(""))

      if (!userClass.uploadAssets) {
        Future.successful(Unauthorized)
      } else {
        val futureFiles = request.body.files
        // check if the message exist
        Message.find(messageId).flatMap {
          case None => Future.successful(BadRequest(resKO("Message not found")))
          case Some(message) => {
            val futureAssetIds: Seq[Future[(String, LastError)]] = futureFiles.map {
              futureFile => {
                for {
                  assetId <- Future(IdHelper.generateAssetId())
                  file <- futureFile.ref
                  // add messageId and assetId to file in gridfs
                  assetResult <- {
                    val query = BSONDocument("_id" -> file.id)
                    val set = BSONDocument("$set" -> BSONDocument(
                      "messageId" -> messageId,
                      "assetId" -> assetId,
                      "user" -> message.from,
                      "created" -> BSONDateTime((new java.util.Date).getTime)))

                    implicit val fileReader = DefaultReadFileReader
                    gridFS.files.update(query, set)
                  }
                  messageResult <- {
                    // create asset object
                    val asset = new Asset(
                      assetId,
                      String.valueOf(file.chunkSize),
                      file.filename,
                      file.contentType.getOrElse("unknown"),
                      new Date)

                    // add asset to message
                    val query = Json.obj("conversationId" -> message.conversationId,
                      "messages.messageId" -> message.messageId)
                    val set = Json.obj("$push" -> Json.obj("messages.$.assets" -> asset))
                    conversationCollection.update(query, set)
                  }
                } yield (assetId, messageResult)
              }
            }

            Future.sequence(futureAssetIds).map {
              results =>
                val errors = results.filter(_._2.inError)

                if (errors.length > 0) {
                  Logger.error("Error updating message: " + errors)
                  InternalServerError(resKO(JsArray(errors.map {
                    case (id,
                    error) => Json.obj(id -> error.stringify)
                  })))
                } else {
                  Ok(resOK(Json.obj("assetIds" -> results.map {
                    case (id, error) => id
                  })))
                }
            }

          }
        }
      }
  }


  def getAsset(token: String, assetId: String) = Action.async {
    request =>
    // everybody is allowed to download assets
      val file = gridFS.find(BSONDocument("assetId" -> new BSONString(assetId)))
      // Frontend always wants inline
      serve(gridFS, file, CONTENT_DISPOSITION_INLINE)
    //        request.getQueryString("inline") match {
    //          case Some("true") => serve(gridFS, file, CONTENT_DISPOSITION_INLINE)
    //          case _ => serve(gridFS, file)
    //        }

  }
}
