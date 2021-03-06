import models.Identity
import org.specs2.time.Duration
import play.api.libs.json.JsObject
import play.api.libs.json.{ JsArray, Json, JsObject }
import play.api.test.{ FakeRequest, FakeApplication }
import play.api.test.Helpers._
import scala.Some
import testHelper.Helper._
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.api.Play.current
import play.api.Logger
import testHelper.{ StartedApp, Helper }
import org.specs2.mutable._
import testHelper.TestConfig._
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

/**
 * User: Björn Reimer
 * Date: 3/3/14
 * Time: 4:48 PM
 */
class ContactControllerSpec extends StartedApp {

  sequential

  var identityOf10thContact = ""
  var idOf10thContact = ""
  var externalContactId = ""
  var numberOfContacts = 0
  val newContactMail = "test@bjrm.de"
  val newContactTel = "+4561233"
  val newContactName = "foobar"
  val newContactNameMixed = "moep"
  val newContactNameMixed2 = "moep2"
  val friendRequestMessage = "whooop! moep!"

  "ContactController" should {

    "get all contacts" in {
      val path = basePath + "/contacts"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beGreaterThan(40)
      numberOfContacts = data.length

      val contact = data(10)
      (contact \ "groups").asOpt[Seq[String]] must beSome
      (contact \ "identityId").asOpt[String] must beSome
      identityOf10thContact = (contact \ "identityId").as[String]
      (contact \ "id").asOpt[String] must beSome
      idOf10thContact = (contact \ "id").as[String]
      (contact \ "identity" \ "displayName").asOpt[String] must beSome
    }

    "get all contacts with offset" in {

      val path = basePath + "/contacts?offset=10"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(numberOfContacts - 10)
    }

    "get all contacts with limit" in {

      val path = basePath + "/contacts?limit=20"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(20)
    }

    "get all contacts with limit and offset" in {
      val path = basePath + "/contacts?offset=10&limit=20"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(20)
      (data(0) \ "identityId").asOpt[String] must beSome(identityOf10thContact)
    }

    "get internal contact" in {

      val path = basePath + "/contact/" + internalContactId

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "identityId").asOpt[String] must beSome(internalContactIdentityId)
      (data \ "contactType").asOpt[String] must beSome("internal")
    }
    val newGroups: Seq[String] = Seq("group1", "group4")

    "edit groups of internal contact" in {

      val path = basePath + "/contact/" + internalContactId

      val json = Json.obj("groups" -> newGroups)

      val req = FakeRequest(PUT, path).withJsonBody(json).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "refuse to edit mail of internal contact" in {
      val path = basePath + "/contact/" + internalContactId

      val newMail = "new@mail.de"
      val json = Json.obj("email" -> newMail)

      val req = FakeRequest(PUT, path).withJsonBody(json).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      status(res) must equalTo(BAD_REQUEST)
    }

    "refuse to edit phoneNumber of internal contact" in {
      val path = basePath + "/contact/" + internalContactId

      val newPhone = "+142536"
      val json = Json.obj("phoneNumber" -> newPhone)

      val req = FakeRequest(PUT, path).withJsonBody(json).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      status(res) must equalTo(BAD_REQUEST)
    }

    "refuse to edit DisplayName of internal contact" in {
      val path = basePath + "/contact/" + internalContactId

      val newName = "fail"
      val json = Json.obj("displayName" -> newName)

      val req = FakeRequest(PUT, path).withJsonBody(json).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      status(res) must equalTo(BAD_REQUEST)
    }

    val mail = "some@mail.com"
    val tel = "+123456789123"
    val name = "foo"

    "add external contact" in {
      val path = basePath + "/contact"
      val json = Json.obj("groups" -> Seq("group1", "group2"),
        "identity" -> Json.obj("email" -> mail, "phoneNumber" -> tel, "displayName" -> name))

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting)).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome
      externalContactId = (data \ "id").as[String]
      (data \ "groups")(0).asOpt[String] must beSome("group1")
      (data \ "groups")(1).asOpt[String] must beSome("group2")
      (data \ "contactType").asOpt[String] must beSome("external")
      (data \ "identity" \ "email" \ "value").asOpt[String] must beSome(mail)
      (data \ "identity" \ "phoneNumber" \ "value").asOpt[String] must beSome(tel)
      (data \ "identity" \ "displayName").asOpt[String] must beSome(name)

    }

    "get the new external contact" in {
      val path = basePath + "/contact/" + externalContactId

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome(externalContactId)
      (data \ "groups")(0).asOpt[String] must beSome("group1")
      (data \ "groups")(1).asOpt[String] must beSome("group2")
      (data \ "contactType").asOpt[String] must beSome("external")
      (data \ "identity" \ "email" \ "value").asOpt[String] must beSome(mail)
      (data \ "identity" \ "phoneNumber" \ "value").asOpt[String] must beSome(tel)
      (data \ "identity" \ "displayName").asOpt[String] must beSome(name)
    }

    "edit groups of external contact" in {
      val path = basePath + "/contact/" + externalContactId

      val newGroups = Seq("group1", "group3")
      val json = Json.obj("groups" -> newGroups)

      val req = FakeRequest(PUT, path).withJsonBody(json).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "edit details of new contact" in {
      val path = basePath + "/contact/" + externalContactId

      val json = Json.obj("email" -> newContactMail, "phoneNumber" -> newContactTel, "displayName" -> newContactName)

      val req = FakeRequest(PUT, path).withJsonBody(json).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "the external contact should contain new values" in {
      val path = basePath + "/contact/" + externalContactId

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome(externalContactId)
      (data \ "groups")(0).asOpt[String] must beSome("group1")
      (data \ "groups")(1).asOpt[String] must beSome("group3")
      (data \ "contactType").asOpt[String] must beSome("external")
      (data \ "identity" \ "email" \ "value").asOpt[String] must beSome(newContactMail)
      (data \ "identity" \ "phoneNumber" \ "value").asOpt[String] must beSome(newContactTel)
      (data \ "identity" \ "displayName").asOpt[String] must beSome(newContactName)
    }

    var externalMixedContactId = ""
    "add new external with email in mixed field" in {
      val path = basePath + "/contact"

      val json = Json.obj("identity" -> Json.obj("mixed" -> validEmails(0), "displayName" -> newContactNameMixed))

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting)).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome
      externalMixedContactId = (data \ "id").as[String]
      (data \ "contactType").asOpt[String] must beSome("external")
      (data \ "identity" \ "email" \ "value").asOpt[String] must beSome(validEmails(0))
      (data \ "identity" \ "phoneNumber" \ "value").asOpt[String] must beNone
      (data \ "identity" \ "displayName").asOpt[String] must beSome(newContactNameMixed)
    }

    "the external contact should be created using the email" in {
      val path = basePath + "/contact/" + externalMixedContactId

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome(externalMixedContactId)
      (data \ "contactType").asOpt[String] must beSome("external")
      (data \ "identity" \ "email" \ "value").asOpt[String] must beSome(validEmails(0))
      (data \ "identity" \ "phoneNumber" \ "value").asOpt[String] must beNone
      (data \ "identity" \ "displayName").asOpt[String] must beSome(newContactNameMixed)
    }

    "add new external with tel in mixed field" in {
      val path = basePath + "/contact"

      val json = Json.obj("identity" -> Json.obj("mixed" -> validPhoneNumbers(0)._1, "displayName" -> newContactNameMixed))

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting)).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome
      externalMixedContactId = (data \ "id").as[String]
      (data \ "contactType").asOpt[String] must beSome("external")
      (data \ "identity" \ "email" \ "value").asOpt[String] must beNone
      (data \ "identity" \ "phoneNumber" \ "value").asOpt[String] must beSome(validPhoneNumbers(0)._2)
      (data \ "identity" \ "displayName").asOpt[String] must beSome(newContactNameMixed)
    }

    "the external contact should be created using the tel" in {
      val path = basePath + "/contact/" + externalMixedContactId

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome(externalMixedContactId)
      (data \ "contactType").asOpt[String] must beSome("external")
      (data \ "identity" \ "email" \ "value").asOpt[String] must beNone
      (data \ "identity" \ "phoneNumber" \ "value").asOpt[String] must beSome(validPhoneNumbers(0)._2)
      (data \ "identity" \ "displayName").asOpt[String] must beSome(newContactNameMixed)
    }

    (invalidEmails ++ invalidPhoneNumbers).map {
      invalid =>
        "refuse to add contact with mixed value: " + invalid in {
          val path = basePath + "/contact"

          val json = Json.obj("identity" -> Json.obj("mixed" -> invalid, "displayName" -> "foooooooooo"))

          val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting)).withJsonBody(json)
          val res = route(req).get

          if (status(res) != BAD_REQUEST) {
            Logger.error("Response: " + contentAsString(res))
          }
          status(res) must equalTo(BAD_REQUEST)
        }
    }

    //    "get all contact groups" in {
    //      val path = basePath + "/contact-groups"
    //
    //      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
    //      val res = route(req).get
    //
    //      if (status(res) != OK) {
    //        Logger.error("Response: " + contentAsString(res))
    //      }
    //      status(res) must equalTo(OK)
    //
    //      val data = (contentAsJson(res) \ "data").as[Seq[String]]
    //
    //      data.find(_.equals("group1")) aka "contain group 1" must beSome
    //      data.find(_.equals("group2")) aka "contain group 2" must beSome
    //      data.find(_.equals("group3")) aka "contain group 3" must beSome
    //      data.find(_.equals("group4")) aka "contain group 4" must beSome
    //    }
    //
    //    "get single group" in {
    //      val path = basePath + "/contact-group/group1"
    //
    //      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
    //      val res = route(req).get
    //
    //      if (status(res) != OK) {
    //        Logger.error("Response: " + contentAsString(res))
    //      }
    //      status(res) must equalTo(OK)
    //
    //      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]
    //
    //      data.length must beGreaterThan(20)
    //
    //    }
    //
    //    "get group with internal contact" in {
    //      val path = basePath + "/contact-group/group4"
    //
    //      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
    //      val res = route(req).get
    //
    //      if (status(res) != OK) {
    //        Logger.error("Response: " + contentAsString(res))
    //      }
    //      status(res) must equalTo(OK)
    //
    //      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]
    //
    //      data.length must beEqualTo(1)
    //
    //      (data(0) \ "identityId").asOpt[String] must beSome(internalContactIdentityId)
    //    }
    //
    //    "get group with created external contact" in {
    //      val path = basePath + "/contact-group/group3"
    //
    //      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
    //      val res = route(req).get
    //
    //      if (status(res) != OK) {
    //        Logger.error("Response: " + contentAsString(res))
    //      }
    //      status(res) must equalTo(OK)
    //
    //      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]
    //
    //      data.length must beEqualTo(1)
    //
    //      (data(0) \ "id").asOpt[String] must beSome(externalContactId)
    //      (data(0) \ "groups")(0).asOpt[String] must beSome("group1")
    //      (data(0) \ "groups")(1).asOpt[String] must beSome("group3")
    //      (data(0) \ "contactType").asOpt[String] must beSome("external")
    //      (data(0) \ "identity" \ "email" \ "value").asOpt[String] must beSome(newContactMail)
    //      (data(0) \ "identity" \ "phoneNumber" \ "value").asOpt[String] must beSome(newContactTel)
    //      (data(0) \ "identity" \ "displayName").asOpt[String] must beSome(newContactName)
    //    }

    "send FriendRequest with identityId" in {
      val path = basePath + "/friendRequest"

      val json = Json.obj("identityId" -> identityExisting2, "message" -> friendRequestMessage)

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting)).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "refuse to send FriendRequest to invalid identityId" in {
      val path = basePath + "/friendRequest"

      val json = Json.obj("identityId" -> "asdfasdf")

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting)).withJsonBody(json)
      val res = route(req).get

      status(res) must equalTo(NOT_FOUND)
    }

    //    "send FriendRequest with cameoId" in {
    //      val path = basePath + "/friendRequest"
    //
    //      val json = Json.obj("cameoId" -> login)
    //
    //      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting2)).withJsonBody(json)
    //      val res = route(req).get
    //
    //      if (status(res) != OK) {
    //        Logger.error("Response: " + contentAsString(res))
    //      }
    //      status(res) must equalTo(OK)
    //    }

    "refuse to send FriendRequest to invalid cameoId" in {
      val path = basePath + "/friendRequest"

      val json = Json.obj("cameoId" -> "pups")

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting)).withJsonBody(json)
      val res = route(req).get

      status(res) must equalTo(NOT_FOUND)
    }

    "send another FriendRequest" in {
      val path = basePath + "/friendRequest"

      val json = Json.obj("identityId" -> identityExisting2)

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting)).withJsonBody(json)
      val res = route(req).get

      status(res) must equalTo(232)
    }

    "get friendRequests and check that there is only one" in {
      val path = basePath + "/friendRequests"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting2))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(1)

      (data(0) \ "identityId").asOpt[String] must beSome(identityExisting)
      (data(0) \ "identity").asOpt[JsObject] must beSome
      (data(0) \ "message").asOpt[String] must beSome(friendRequestMessage)
    }

    "reject FriendRequest" in {

      val path = basePath + "/friendRequest/answer"
      val json = Json.obj("answerType" -> "reject", "identityId" -> identityExisting)

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting2)).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "check that friendRequest is gone" in {
      val path = basePath + "/friendRequests"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting2))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(0)
    }

    "send another FriendRequest" in {
      val path = basePath + "/friendRequest"

      val json = Json.obj("identityId" -> identityExisting2)

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting)).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "check if it appears in contacts as pending" in {
      val path = basePath + "/contacts"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      val contact = data.find(js => (js \ "identityId").as[String].equals(identityExisting2))

      contact must beSome

      (contact.get \ "contactType").asOpt[String] must beSome("pending")
      (contact.get \ "identity").asOpt[JsObject] must beSome
      (contact.get \ "id").asOpt[String] must beSome("")
    }

    "recipient send FriendRequest" in {
      val path = basePath + "/friendRequest"

      val json = Json.obj("identityId" -> identityExisting)

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting2)).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "accept FriendRequest" in {
      val path = basePath + "/friendRequest/answer"

      val json = Json.obj("answerType" -> "accept", "identityId" -> identityExisting)

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting2)).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    var senderContactId = ""

    "check if contact was added to sender (and only once)" in {
      val path = basePath + "/contacts"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting2))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      val contact = data.partition(c => (c \ "identityId").as[String].equals(identityExisting))._1
      contact.length must beEqualTo(1)
      (contact(0) \ "id").asOpt[String] must beSome
      senderContactId = (contact(0) \ "id").as[String]
      (contact(0) \ "identity").asOpt[JsObject] must beSome
    }

    var receiverContactId = ""
    "check if contact was added to receiver (and only once)" in {
      val path = basePath + "/contacts"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      val contact = data.partition(c => (c \ "identityId").as[String].equals(identityExisting2))._1
      contact.length must beEqualTo(1)
      (contact(0) \ "id").asOpt[String] must beSome
      receiverContactId = (contact(0) \ "id").as[String]
      (contact(0) \ "identity").asOpt[JsObject] must beSome
    }

    "check if friendRequest is gone" in {
      val path = basePath + "/friendRequests"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting2))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(0)
    }

    "check if friendRequest of receiver is gone" in {
      val path = basePath + "/friendRequests"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.count(js =>
        (js \ "identityId").as[String].equals(identityExisting2)
      ) must beEqualTo(0)
    }

    "refuse to send friend request to identity that is already in contacts" in {
      val path = basePath + "/friendRequest"

      val json = Json.obj("identityId" -> identityExisting2)

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting)).withJsonBody(json)
      val res = route(req).get

      status(res) must equalTo(232)
    }

    "delete contact 1" in {
      val path = basePath + "/contact/" + senderContactId
      val req = FakeRequest(DELETE, path).withHeaders(tokenHeader(tokenExisting2))
      val res = route(req).get
      status(res) must equalTo(200)
    }

    "delete contact 2" in {
      val path = basePath + "/contact/" + receiverContactId
      val req = FakeRequest(DELETE, path).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get
      status(res) must equalTo(200)
    }

    "send another FriendRequest" in {
      val path = basePath + "/friendRequest"

      val json = Json.obj("identityId" -> identityExisting2)

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting)).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "ignore FriendRequest" in {
      val path = basePath + "/friendRequest/answer"

      val json = Json.obj("answerType" -> "ignore", "identityId" -> identityExisting)

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting2)).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "ignored identity should not be in contacts" in {
      val path = basePath + "/contacts"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting2))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.find(c => (c \ "identityId").as[String].equals(identityExisting)) must beNone
    }

    "check that friendRequest is gone" in {
      val path = basePath + "/friendRequests"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting2))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(0)
    }

    "send another FriendRequest to ignoring identity" in {
      val path = basePath + "/friendRequest"

      val json = Json.obj("identityId" -> identityExisting2)

      val req = FakeRequest(POST, path).withHeaders(tokenHeader(tokenExisting)).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "no friend request should be received" in {
      val path = basePath + "/friendRequests"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(tokenExisting2))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(0)
    }

    "delete Contacts" should {

      val testUser1 = TestUser.create()
      val testUser2 = TestUser.create()

      testUser1.makeFriends(testUser2)
      val externalContact = testUser1.addExternalContact(Some("0123456"), Some("foo@moep.de"))
      var internalContactId = ""

      "get internal contact id" in {

        val data = getDataSeq(executeRequest(GET, "/contacts", OK, Some(testUser1.token)))

        data.length must beEqualTo(3)
        val internalContact = data.find(js => (js \ "identityId").as[String].equals(testUser2.identityId))
        internalContact must beSome

        internalContactId = (internalContact.get \ "id").as[String]

        1 === 1
      }

      "refuse to delete non-existing contact" in {
        checkError(executeRequest(DELETE, "/contact/asdfasdf", NOT_FOUND, Some(testUser1.token)))
      }

      "delete internal Contact" in {
        checkOk(executeRequest(DELETE, "/contact/" + internalContactId, OK, Some(testUser1.token)))
      }

      "the internal contact should be deleted" in {
        checkError(executeRequest(GET, "/contact/" + internalContactId, NOT_FOUND, Some(testUser1.token)))
      }

      "the other identity should still have the contact" in {
        val data = getDataSeq(executeRequest(GET, "/contacts", OK, Some(testUser2.token)))

        data.length must beEqualTo(2)
        data.find(js => (js \ "identityId").as[String].equals(testUser1.identityId)) must beSome
      }

      "delete external contact" in {
        checkOk(executeRequest(DELETE, "/contact/" + externalContact._1, OK, Some(testUser1.token)))
      }

      "the external contact should be deleted" in {
        checkError(executeRequest(GET, "/contact/" + externalContact._1, NOT_FOUND, Some(testUser1.token)))
      }

      "the identity of the external contact should still exist, but not have any contact details" in {
        val maybeIdentity = Await.result(Identity.find(externalContact._2), FiniteDuration(1, "min"))

        maybeIdentity must beSome

        maybeIdentity.get.phoneNumber must beNone
        maybeIdentity.get.email must beNone
        maybeIdentity.get.avatar must beSome
        maybeIdentity.get.displayName must beSome
      }

      "there should be no more contacts left (except support)" in {
        val data = getDataSeq(executeRequest(GET, "/contacts", OK, Some(testUser1.token)))

        data.length must beEqualTo(1)
        data.find(js => (js \ "identityId").as[String].equals(testUser2.identityId)) must beNone
      }

      "the other identity should not be able to send a friend request, since it still has the contact" in {
        val body = Json.obj("identityId" -> testUser1.identityId, "message" -> "moep")
        checkError(executeRequest(POST, "/friendRequest", 232, Some(testUser2.token), Some(body)))
      }

      "send FriendRequest to deleted internal contact and accept it" in {
        testUser1.makeFriends(testUser2) must beEqualTo(OK)
      }

      "The first identity should have the other as contact again" in {
        val data = getDataSeq(executeRequest(GET, "/contacts", OK, Some(testUser1.token)))

        data.length must beEqualTo(2)
        data.count(js => (js \ "identityId").as[String].equals(testUser2.identityId)) must beEqualTo(1)
      }

      "The other identity should have the other still as contact and only once" in {
        val data = getDataSeq(executeRequest(GET, "/contacts", OK, Some(testUser2.token)))

        data.length must beEqualTo(2)
        data.count(js => (js \ "identityId").as[String].equals(testUser1.identityId)) must beEqualTo(1)
      }
    }

    "delete friend requests" should {

      val testUser1 = TestUser.create()
      val testUser2 = TestUser.create()

      "send friend request to other identity" in {
        val body = Json.obj("identityId" -> testUser2.identityId)
        checkOk(executeRequest(POST, "/friendRequest", OK, Some(testUser1.token), Some(body)))
      }

      "sender of the friend request should see it in his contacts" in {
        val data = getDataSeq(executeRequest(GET, "/contacts", OK, Some(testUser1.token)))

        data.length must beEqualTo(2)
        data.find(js => (js \ "identityId").as[String].equals(testUser2.identityId)) must beSome
      }

      "the receiver should see the friend request" in {
        val data = getDataSeq(executeRequest(GET, "/friendRequests", OK, Some(testUser2.token)))

        data.length must beEqualTo(1)
        data.find(js => (js \ "identityId").as[String].equals(testUser1.identityId)) must beSome
      }

      "refuse to delete friend request that does not exist" in {
        checkError(executeRequest(DELETE, "/friendRequest/" + identityExisting, BAD_REQUEST, Some(testUser1.token)))
      }

      "sender deletes friend request" in {
        checkOk(executeRequest(DELETE, "/friendRequest/" + testUser2.identityId, OK, Some(testUser1.token)))
      }

      "sender should not see the friend request in his contacts" in {
        val data = getDataSeq(executeRequest(GET, "/contacts", OK, Some(testUser1.token)))

        data.length must beEqualTo(1)
        data.find(js => (js \ "identityId").as[String].equals(testUser2.identityId)) must beNone
      }

      "the receiver should not see the friend request" in {
        val data = getDataSeq(executeRequest(GET, "/friendRequests", OK, Some(testUser2.token)))

        data.length must beEqualTo(0)
      }
    }
  }
}
