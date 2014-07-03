package models

import java.util.Date

import constants.Messaging._
import helper.IdHelper
import helper.JsonHelper._
import helper.MongoCollections._
import models.cockpit.CockpitListFilter
import models.cockpit.attributes.{ CockpitAttributeFilter, CockpitAttributeString, _ }
import play.api.Play
import play.api.Play.current
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{ JsObject, _ }
import reactivemongo.core.commands.LastError
import traits.{ CockpitAttribute, CockpitEditable, Model }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

/**
 * User: Björn Reimer
 * Date: 1/16/14
 * Time: 4:19 PM
 */
case class Account(id: MongoId,
                   loginName: String,
                   password: String,
                   identities: Seq[MongoId],
                   phoneNumber: Option[String], // not used anymore
                   email: Option[String], // not used anymore
                   created: Date,
                   lastUpdated: Date) {

  def toJson: JsObject = Json.toJson(this)(Account.outputWrites).as[JsObject]

  def toJsonWithIdentities: Future[JsObject] = {
    val js = this.identities.map {
      iId =>
        Identity.find(iId).map {
          case None    => Json.obj()
          case Some(i) => i.toPrivateJson
        }
    }

    Future.sequence(js).map {
      futureIdentities => this.toJson ++ Json.obj("identities" -> futureIdentities)
    }
  }
}

object Account extends Model[Account] with CockpitEditable[Account] {

  def col = accountCollection

  implicit val mongoFormat: Format[Account] = createMongoFormat(Json.reads[Account], Json.writes[Account])

  def docVersion = 0

  def evolutions = Map()

  def createReads: Reads[Account] = {
    val id = IdHelper.generateAccountId()
    (Reads.pure[MongoId](id) and
      (__ \ 'loginName).read[String] and
      (__ \ 'password).read[String](minLength[String](8) andKeep hashPassword) and
      Reads.pure[Seq[MongoId]](Seq()) and
      (__ \ 'phoneNumber).readNullable[String](verifyPhoneNumber andThen Reads.StringReads) and
      (__ \ 'email).readNullable[String](verifyMail andThen Reads.StringReads) and
      Reads.pure[Date](new Date()) and
      Reads.pure[Date](new Date()))(Account.apply _)
  }

  def outputWrites: Writes[Account] = Writes {
    a =>
      Json.obj("id" -> a.id.toJson) ++
        Json.obj("loginName" -> a.loginName) ++
        Json.obj("identities" -> a.identities.map(id => id.toJson)) ++
        addCreated(a.created) ++
        addLastUpdated(a.lastUpdated)
  }

  def findByLoginName(loginName: String): Future[Option[Account]] = {
    val query = Json.obj("loginName" -> loginName)
    col.find(query).one[Account]
  }

  def findAlternative(loginName: String, count: Int = 1): Future[String] = {
    val currentTry = loginName + "_" + count

    val loginExists: Future[Boolean] = for {
      account <- Account.findByLoginName(currentTry)
      identity <- Identity.findByCameoId(currentTry)
    } yield {
      account.isDefined || identity.isDefined
    }

    loginExists.flatMap {
      case true => findAlternative(loginName, count + 1) // recursive futures ftw!
      case false =>
        // check if it is reserved
        AccountReservation.checkReserved(currentTry).flatMap {
          case Some(r) => findAlternative(loginName, count + 1)
          case None    => Future(currentTry)
        }
    }
  }

  def createDefault(): Account = {
    new Account(IdHelper.generateAccountId(), IdHelper.randomString(8), "", Seq(), None, None, new Date, new Date)
  }

  def cockpitMapping: Seq[CockpitAttribute] = {
    val pmtOptions = Seq(MESSAGE_TYPE_DEFAULT, MESSAGE_TYPE_EMAIL, MESSAGE_TYPE_SMS)

    Seq(
      CockpitAttributeString[String](name = "loginName", displayName = "Login Name", nullValue = "", showInList = true),
      CockpitAttributeString[String](name = "password", displayName = "Password", nullValue = ""),
      CockpitAttributeString[Option[String]](name = "phoneNumber", displayName = "Phone Number", nullValue = None, isEditable = true, showInList = true),
      CockpitAttributeString[Option[String]](name = "email", displayName = "Email", nullValue = None, isEditable = true, showInList = true),
      CockpitAttributeFilter("identities", "Identities", "identity", "ID"),
      CockpitAttributeDate(name = "created", displayName = "Created"),
      CockpitAttributeDate(name = "lastUpdated", displayName = "Last Updated (not working yet)")
    )
  }

  def cockpitListFilters: Seq[CockpitListFilter] = Seq(
    new CockpitListFilter("ID", str => Json.obj("_id.mongoId" -> Json.obj("$regex" -> str))),
    new CockpitListFilter("LoginName", str => Json.obj("loginName" -> Json.obj("$regex" -> str.toLowerCase))),
    new CockpitListFilter("Email", str => Json.obj("email" -> Json.obj("$regex" -> str))),
    new CockpitListFilter("PhoneNumber", str => Json.obj("phoneNumber" -> Json.obj("$regex" -> str)))
  )

}

case class AccountReservation(loginName: String,
                              id: MongoId,
                              created: Date) {

  def toJson: JsObject = {
    Json.obj("loginName" -> this.loginName) ++
      Json.obj("reservationSecret" -> this.id.toString)
  }
}

object AccountReservation extends Model[AccountReservation] {

  implicit val col = reservedAccountCollection

  implicit val mongoFormat: Format[AccountReservation] = createMongoFormat(Json.reads[AccountReservation], Json.writes[AccountReservation])

  def docVersion: Int = 0

  def evolutions: Map[Int, Reads[JsObject]] = Map()

  def reserve(loginName: String): Future[AccountReservation] = {
    val res = new AccountReservation(loginName, IdHelper.generateReservationSecret(), new Date)
    col.insert(res).map {
      lastError => res
    }
  }

  def findByLoginName(loginName: String): Future[Option[AccountReservation]] = {
    val query = Json.obj("loginName" -> loginName)
    col.find(query).one[AccountReservation]
  }

  def deleteReserved(loginName: String): Future[LastError] = {
    val query = Json.obj("loginName" -> loginName)
    col.remove(query)
  }

  def createDefault(): AccountReservation = {
    new AccountReservation(IdHelper.randomString(8), IdHelper.generateMongoId(), new Date)
  }
}
