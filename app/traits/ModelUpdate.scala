package traits

import helper.ResultHelper._
import models.{ MongoId, VerifiedString }
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.Result

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * User: Björn Reimer
 * Date: 13.10.14
 * Time: 13:53
 */

trait UpdateValue {
  def name: String
  def externalEdit: Boolean
  def fromJson(json: JsValue): JsResult[Option[JsObject]]
  def fromValue(value: Any): Option[JsObject]
}

case class StringUpdateValue(name: String, externalEdit: Boolean = false) extends UpdateValue {

  def fromJson(json: JsValue): JsResult[Option[JsObject]] = {
    (json \ name).validate[Option[String]].map {
      case None     => Some(Json.obj())
      case Some("") => None
      case Some(s)  => Some(Json.obj(name -> s))
    }
  }

  // todo: type checking at runtime, can do better
  def fromValue(value: Any) = value match {
    case ""        => None
    case s: String => Some(Json.obj(name -> s))
    case any =>
      Logger.error("Not applying update, expected string: " + any)
      Some(Json.obj())
  }
}

case class StringUpdateSubvalue(parentName: String, name: String, externalEdit: Boolean = false) extends UpdateValue {

  def fromJson(json: JsValue): JsResult[Option[JsObject]] =
    (json \ parentName \ name).validate[Option[String]].map {
      case None     => Some(Json.obj())
      case Some("") => None
      case Some(s)  => Some(Json.obj(parentName + "." + name -> s))
    }

  // todo: type checking at runtime, can do better
  def fromValue(value: Any) = value match {
    case ""        => None
    case s: String => Some(Json.obj(parentName + "." + name -> s))
    case any =>
      Logger.error("Not applying update, expected string: " + any)
      Some(Json.obj())
  }
}

case class VerifiedStringUpdateValue(name: String, verify: Reads[JsString] = Reads.JsStringReads, externalEdit: Boolean = false) extends UpdateValue {

  def fromJson(json: JsValue): JsResult[Option[JsObject]] = {
    (json \ name).validate[Option[String]].flatMap {
      case None     => JsSuccess(Some(Json.obj()))
      case Some("") => JsSuccess(None)
      case Some(s) =>
        (json \ name).validate[VerifiedString](verify andThen VerifiedString.createReads).map {
          verifiedString => Some(Json.obj(name -> verifiedString))
        }
    }
  }

  // todo: type checking at runtime, can do better
  def fromValue(value: Any) = value match {
    case vs: VerifiedString if vs.value.equals("") => None
    case vs: VerifiedString                        => Some(Json.obj(name -> vs))
    case any =>
      Logger.error("Not applying update, expected verifiedString: " + any)
      Some(Json.obj())
  }
}

case class MongoIdUpdateValue(name: String, externalEdit: Boolean = false) extends UpdateValue {

  def fromJson(json: JsValue): JsResult[Option[JsObject]] =
    (json \ name).validate[Option[String]].map {
      case None     => Some(Json.obj())
      case Some("") => None
      case Some(s)  => Some(Json.obj(name -> MongoId(s)))
    }

  // todo: type checking at runtime, can do better
  def fromValue(value: Any) = value match {
    case m: MongoId if m.id.equals("") => None
    case m: MongoId                    => Some(Json.obj(name -> m))
    case any =>
      Logger.error("Not applying update, expected MongoId: " + any)
      Some(Json.obj())
  }
}

case class BooleanUpdateValue(name: String, externalEdit: Boolean = false) extends UpdateValue {

  def fromJson(json: JsValue): JsResult[Option[JsObject]] =
    (json \ name).validate[Option[Boolean]].map {
      case None    => Some(Json.obj())
      case Some(b) => Some(Json.obj(name -> b))
    }

  // todo: type checking at runtime, can do better
  def fromValue(value: Any) = value match {
    case s: Boolean => Some(Json.obj(name -> s))
    case any =>
      Logger.error("Not applying update, expected Boolean: " + any)
      Some(Json.obj())
  }
}

case class BooleanUpdateSubvalue(parentName: String, name: String, externalEdit: Boolean = false) extends UpdateValue {

  def fromJson(json: JsValue): JsResult[Option[JsObject]] = {
    (json \ parentName \ name).validate[Option[Boolean]].map {
      case None    => Some(Json.obj())
      case Some(b) => Some(Json.obj(parentName + "." + name -> b))
    }
  }
  // todo: type checking at runtime, can do better
  def fromValue(value: Any) = value match {
    case s: Boolean => Some(Json.obj(parentName + "." + name -> s))
    case any =>
      Logger.error("Not applying update, expected Boolean: " + any)
      Some(Json.obj())
  }
}

trait ModelUpdate {

  def values: Seq[UpdateValue]

  def fromRequest(json: JsValue)(action: ((JsObject => Future[Result]))): Future[Result] = {
    values
      .filter(_.externalEdit)
      .foldLeft[JsResult[JsObject]](JsSuccess(Json.obj())) {
        (res, value) =>
          res.flatMap {
            js =>
              value.fromJson(json).map {
                case None                                => js.deepMerge(Json.obj("$unset" -> Json.obj(value.name -> "")))
                case Some(set) if set.equals(Json.obj()) => js
                case Some(set)                           => js.deepMerge(Json.obj("$set" -> set))
              }
          }
      }
      .map {
        js => action(js)
      }.recoverTotal {
        error => Future(resBadRequest("invalid json", data = Some(JsError.toFlatJson(error))))
      }
  }

  def fromMap(setValues: Map[String, Any]): JsObject = {
    setValues.foldLeft[JsObject](Json.obj()) {
      (res, setValue) =>
        // todo: again kinda type checking at runtime
        values.find(_.name.equals(setValue._1)) match {
          case None =>
            Logger.error("trying to update value that doesn't exist: " + setValue._1)
            res
          case Some(value) =>
            value.fromValue(setValue._2) match {
              case None      => res.deepMerge(Json.obj("$unset" -> Json.obj(value.name -> "")))
              case Some(set) => res.deepMerge(Json.obj("$set" -> set))
            }
        }
    }
  }
}

