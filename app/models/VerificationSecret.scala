package models


import play.api.libs.json._
import helper.IdHelper
import traits.MongoHelper
import java.util.Date
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global

/**
 * User: Björn Reimer
 * Date: 2/3/14
 * Time: 7:15 PM
 */

case class VerificationSecret(
                               id: MongoId,
                               identityId: MongoId,
                               verificationType: String,
                               valueToBeVerified: String,
                               created: Date
                               )

object VerificationSecret extends MongoHelper {


  implicit val mongoFormat: Format[VerificationSecret] = createMongoFormat(Json.reads[VerificationSecret], Json.writes[VerificationSecret])

  implicit def col = verificationCollection

  def create(identityId: MongoId, valueToBeVerified: String, verificationType: String): VerificationSecret = {
    new VerificationSecret(
      IdHelper.generateVerificationSecret(),
      identityId,
      verificationType,
      valueToBeVerified,
      new Date
    )
  }

  def find(id: MongoId): Future[Option[VerificationSecret]] = {
    val query = Json.obj("_id" -> id)
    col.find(query).one[VerificationSecret]
  }


}