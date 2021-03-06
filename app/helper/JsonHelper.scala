package helper

import java.util.Date

import constants.ErrorCodes.ErrorCodes
import models.VerifiedString
import org.mindrot.jbcrypt.BCrypt
import play.api.Play
import play.api.Play.current
import play.api.i18n.Lang
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.modules.reactivemongo.json.BSONFormats
import reactivemongo.bson.BSONDocument
import reactivemongo.core.commands.Match

/**
 * User: Björn Reimer
 * Date: 6/12/13
 * Time: 7:10 PM
 */
object JsonHelper {

  val emptyObj = __.json.put(Json.obj())

  // converts dates to mongo format ($date)
  val toMongoDates: Reads[JsObject] = {
    __.json.update((__ \ 'created \ '$date).json.copyFrom((__ \ 'created).json.pick[JsNumber]) or emptyObj) andThen
      __.json.update((__ \ 'lastUpdated \ '$date).json.copyFrom((__ \ 'lastUpdated).json.pick[JsNumber]) or emptyObj) andThen
      __.json.update((__ \ 'lastAccessed \ '$date).json.copyFrom((__ \ 'lastAccessed).json.pick[JsNumber]) or emptyObj)
  }

  val fromMongoDates: Reads[JsObject] = {
    __.json.update((__ \ 'created).json.copyFrom((__ \ 'created \ '$date).json.pick[JsNumber]) or emptyObj) andThen
      __.json.update((__ \ 'lastUpdated).json.copyFrom((__ \ 'lastUpdated \ '$date).json.pick[JsNumber]) or emptyObj) andThen
      __.json.update((__ \ 'lastAccessed).json.copyFrom((__ \ 'lastAccessed \ '$date).json.pick[JsNumber]) or emptyObj)
  }

  // converts id to _id
  val toMongoId: Reads[JsObject] = {
    __.json.update((__ \ '_id).json.copyFrom((__ \ 'id).json.pick[JsValue]) or emptyObj) andThen
      (__ \ 'id).json.prune
  }

  val fromMongoId: Reads[JsObject] = {
    __.json.update((__ \ 'id).json.copyFrom((__ \ '_id).json.pick[JsValue]) or emptyObj) andThen
      (__ \ '_id).json.prune
  }

  def addCreated(date: Date): JsObject = {
    Json.obj("created" -> PrintDate.toString(date))
  }

  def addLastUpdated(date: Date): JsObject = {
    Json.obj("lastUpdated" -> PrintDate.toString(date))
  }

  def addErrorCodes(errorCodes: ErrorCodes): JsObject = {
    errorCodes.length match {
      case 0 => Json.obj()
      case _ => Json.obj("errorCodes" -> errorCodes)
    }
  }

  def maybeEmptyJson[A](key: String, value: Option[A])(implicit writes: Writes[A]): JsObject = {
    value match {
      case Some(s) => Json.obj(key -> value)
      case None    => Json.obj()
    }
  }

  def getCameoId(base: String): JsObject = {
    val domain = Play.configuration.getString("domain").get
    Json.obj("cameoId" -> (base + "@" + domain))
  }

  def getNewValueVerifiedString(old: Option[VerifiedString], newValue: VerifiedString): Option[VerifiedString] = {
    if (old.isDefined && old.get.value.equals(newValue.value)) {
      None
    } else {
      Some(newValue)
    }
  }

  def getNewValue[A](old: Option[A], newValue: A): Option[A] = {
    if (old.isDefined && old.get.equals(newValue)) {
      None
    } else {
      Some(newValue)
    }
  }

  def toBson(json: JsValue): Option[BSONDocument] = {
    BSONFormats.toBSON(json).asOpt.map(_.asInstanceOf[BSONDocument])
  }

  def toMatch(json: JsValue): Match = {
    Match(toBson(json).get)
  }

  val hashPassword: Reads[String] = Reads[String] {
    js =>
      js.validate[String].map {
        password =>
          val hashed = BCrypt.hashpw(password, BCrypt.gensalt())
          hashed
      }
  }

  val toLowerCase: Reads[String] = Reads[String] {
    js => js.validate[String].map(_.toLowerCase)
  }

  val verifyLanguageId: Reads[String] = Reads[String] {
    js =>
      js.asOpt[String] match {
        case None => JsError("no language")
        case Some(lang) =>
          try {
            val l = Lang(lang)
            JsSuccess(lang)
          } catch {
            case e: RuntimeException => JsError("invalid language code")
          }
      }
  }

  def limitArray(name: String, limit: Int, offset: Int): JsObject = {

    // this is not very elegant, but there seems to be no way to get offset without limit in mongodb...
    def maxLimit = Int.MaxValue

    (limit, offset) match {
      // no restrictions
      case (0, 0) => Json.obj()
      // offset only
      case (0, _) => Json.obj(name -> Json.obj("$slice" -> Seq(offset, maxLimit)))
      // limit only
      case (_, 0) => Json.obj(name -> Json.obj("$slice" -> limit))
      // offset and limit
      case (_, _) => Json.obj(name -> Json.obj("$slice" -> Seq(offset, limit)))
    }
  }

  def verifyMail: Reads[JsString] = Reads[JsString] {
    js =>
      js.validate[String].flatMap {
        mail =>
          CheckHelper.checkAndCleanEmail(mail) match {
            case None          => JsError("invalid email: " + mail)
            case Some(checked) => JsSuccess(JsString(checked))
          }
      }
  }

  def verifyPhoneNumber: Reads[JsString] = Reads[JsString] {
    js =>
      js.validate[String].flatMap {
        phoneNumber =>
          CheckHelper.checkAndCleanPhoneNumber(phoneNumber) match {
            case None          => JsError("invalid phoneNumber: " + phoneNumber)
            case Some(checked) => JsSuccess(JsString(checked))
          }
      }
  }
}
