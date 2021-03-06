
import constants.ErrorCodes
import org.specs2.matcher.MatchResult
import play.api.libs.json._
import play.api.test._
import play.api.test.FakeApplication
import play.api.test.Helpers._
import scala.Some
import testHelper.Helper._
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.api.Play.current
import play.api.{ Play, Logger }
import testHelper.{ TestConfig, StartedApp, Helper }
import org.specs2.mutable._
import testHelper.TestConfig._

/**
 * User: Björn Reimer
 * Date: 3/3/14
 * Time: 4:48 PM
 */
class AccountControllerSpec extends StartedApp {

  sequential

  val login = "superMoep"
  val login2 = "monsterMoep"
  val loginExternal = "superMonsterMoep"
  val pass = randomString(8)
  val displayName = "MOEP"
  val mail = validEmails(0)
  val tel = validPhoneNumbers(0)._1
  val cleanedTel = validPhoneNumbers(0)._2
  val displayName2 = "MOEP2"
  val mail2 = validEmails(1)
  val tel2 = validPhoneNumbers(1)._2
  var identityId = ""
  var token = ""
  var regSec = ""
  var regSec2 = ""

  val newIdentityDisplayName = "Mooeeppss"
  val newIdentityTel = "+49123456"
  val newIdentityEmail = "devnull@cameo.io"
  val newIdentityCameoId = "myMoepDieMoep"
  var newIdentityId = ""

  "AccountController" should {

    "Get existing account" in {

      val data = getData(executeRequest(GET, "/account", OK, Some(tokenExisting)))

      (data \ "id").asOpt[String] must beSome
      (data \ "loginName").asOpt[String] must beSome
      (data \ "phoneNumber" \ "value").asOpt[String] must beSome
      (data \ "phoneNumber" \ "isVerified").asOpt[Boolean] must beSome
      (data \ "email" \ "value").asOpt[String] must beSome
      (data \ "email" \ "isVerified").asOpt[Boolean] must beSome
      (data \ "identities").asOpt[Seq[JsObject]] must beSome
      (data \ "userSettings").asOpt[JsObject] must beSome
      (data \ "userSettings" \ "enableUnreadMessages").asOpt[Boolean] must beSome(true)
      (data \ "userSettings" \ "convertSmileysToEmojis").asOpt[Boolean] must beSome(true)
      (data \ "userSettings" \ "sendOnReturn").asOpt[Boolean] must beSome(false)
      (data \ "userSettings" \ "languageSettings").asOpt[String] must beSome
      (data \ "userSettings" \ "dateFormat").asOpt[String] must beSome
      (data \ "userSettings" \ "timeFormat").asOpt[String] must beSome
    }

    val newSettings = Json.obj(
      "enableUnreadMessages" -> false,
      "convertSmileysToEmojis" -> false,
      "sendOnReturn" -> true,
      "languageSettings" -> "foo",
      "dateFormat" -> "baa",
      "timeFormat" -> "moep"
    )

    "Edit account settings" in {
      val body = Json.obj("userSettings" -> newSettings)
      checkOk(executeRequest(PUT, "/account", OK, Some(tokenExisting2), Some(body)))
    }

    "Account should contain new settings" in {
      val data = getData(executeRequest(GET, "/account", OK, Some(tokenExisting2)))
      (data \ "userSettings").asOpt[JsObject] must beSome(newSettings)
    }

    invalidLogins.map {
      invalidLogin =>
        "Refuse invalid Login: " + invalidLogin in {
          val body = Json.obj("loginName" -> invalidLogin)
          checkError(executeRequest(POST, "/account/check", BAD_REQUEST, body = Some(body)))
        }
    }

    "Reserve Login" in {
      val body = Json.obj("loginName" -> login)
      val data = getData(executeRequest(POST, "/account/check", OK, body = Some(body)))

      val regSeqOpt = (data \ "reservationSecret").asOpt[String]

      if (regSeqOpt.isDefined) {
        regSec = regSeqOpt.get
      }

      regSeqOpt aka "returned registration secret" must beSome
    }

    "Reserve another Login" in {
      val body = Json.obj("loginName" -> login2)
      val data = getData(executeRequest(POST, "/account/check", OK, body = Some(body)))

      val regSeqOpt = (data \ "reservationSecret").asOpt[String]

      regSeqOpt must beSome
      regSec2 = regSeqOpt.get

      1 === 1
    }

    validLogins.map{ validLogin =>
      "Accept valid login: " + validLogin in {
        val body = Json.obj("loginName" -> validLogin)
        checkOk(executeRequest(POST, "/account/check", OK, body = Some(body)))
      }
    }

    "Refuse to reserve reserved loginName and return alternative" in {
      val body = Json.obj("loginName" -> login)
      val data = getData(executeRequest(POST, "/account/check", 232, body = Some(body)))
      (data \ "alternative").asOpt[String] must beSome(login + "_1")
    }

    "Refuse to reserve existing login and return alternative" in {
      val body = Json.obj("loginName" -> loginExisting)
      val data = getData(executeRequest(POST, "/account/check", 232, body = Some(body)))
      (data \ "alternative").asOpt[String] must beSome(loginExisting + "_1")
    }

    "Refuse to reserve reserved existing cameoId and return alternative" in {
      val body = Json.obj("loginName" -> cameoIdExisting)
      val data = getData(executeRequest(POST, "/account/check", 232, body = Some(body)))
      (data \ "alternative").asOpt[String] must beSome(cameoIdExisting + "_1")
    }

    def checkLogin(loginName: String) = {
      val body = Json.obj("loginName" -> loginName)
      checkOk(executeRequest(POST, "/account/check", OK, body = Some(body)))
    }

    "allow to reserve loginName that contains a reserved name" in {
      checkLogin(login + "moep")
    }

    "allow to reserve loginName that contains a reserved name" in {
      checkLogin("moep" + login)
    }

    "allow to reserve loginName that contains a reserved name" in {
      checkLogin("moep" + login + "moep")
    }

    "allow to reserve loginName that is a substring of a reserved name" in {
      checkLogin(login.substring(2))
    }

    "allow to reserve loginName that contains an existing login" in {
      checkLogin(loginExisting + "moep")
    }

    "allow to reserve loginName that contains an existing login" in {
      checkLogin(loginExisting + login)
    }

    "allow to reserve loginName that contains an existing login" in {
      checkLogin(loginExisting + login + "moep")
    }

    "allow to reserve loginName that is a substring of an existing login" in {
      checkLogin(loginExisting.substring(2))
    }

    "allow to reserve loginName that contains an existing cameoId" in {
      checkLogin(cameoIdExisting + "moep")
    }

    "allow to reserve loginName that contains an existing cameoId" in {
      checkLogin(cameoIdExisting + login)
    }

    "allow to reserve loginName that contains an existing cameoId" in {
      checkLogin(cameoIdExisting + login + "moep")
    }

    "allow to reserve loginName that is a substring of an existing cameoId" in {
      checkLogin(cameoIdExisting.substring(2))
    }

    "Refuse to reserve reserved loginName with different capitalization" in {
      val body = Json.obj("loginName" -> login.toUpperCase)
      val data = getData(executeRequest(POST, "/account/check", 232, body = Some(body)))
      (data \ "alternative").asOpt[String] must beSome(login.toUpperCase + "_1")
    }

    "Refuse to claim reserved login without secret" in {
      val body = createUser(login, pass)
      checkError(executeRequest(POST, "/account", BAD_REQUEST, body = Some(body)))
    }

    "Refuse to register with wrong loginName for secret" in {
      val body = createUser(login + "a", pass) ++ Json.obj("reservationSecret" -> regSec)
      checkError(executeRequest(POST, "/account", BAD_REQUEST, body = Some(body)))
    }

    "Create Account" in {
      val body = createUser(login, pass) ++ Json.obj("reservationSecret" -> regSec)
      val data = getData(executeRequest(POST, "/account", OK, body = Some(body), apiVersion = "v2"))
      (data \ "identities").asOpt[Seq[JsObject]] must beNone
      (data \ "registrationIncomplete").asOpt[Boolean] must beSome(true)
    }

    "Refuse to register again with same secret" in {
      val path = basePath + "/account"
      val json = createUser(login + "a", pass) ++ Json.obj("reservationSecret" -> regSec)

      val req = FakeRequest(POST, path).withJsonBody(json)
      val res = route(req).get

      status(res) must equalTo(BAD_REQUEST)
    }

    "Refuse to reserve existing loginName and return next alternative" in {
      val path = basePath + "/account/check"
      val json = Json.obj("loginName" -> login)

      val req = FakeRequest(POST, path).withJsonBody(json)
      val res = route(req).get

      status(res) must equalTo(232)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "alternative").asOpt[String] must beSome(login + "_1")
    }

    "Refuse to reserve loginName that is an existing CameoId and return alternative" in {
      val path = basePath + "/account/check"
      val json = Json.obj("loginName" -> cameoIdExisting)

      val req = FakeRequest(POST, path).withJsonBody(json)
      val res = route(req).get

      status(res) must equalTo(232)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "alternative").asOpt[String] must beSome(cameoIdExisting + "_1")
    }

    "return error when getting a token without an identity" in {
      val path = basePath + "/token"

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((login + ":" + pass).getBytes)

      val req = FakeRequest(GET, path).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != 232) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(232)

      (contentAsJson(res) \ "errorCodes").asOpt[Seq[String]] must beSome(ErrorCodes.ACCOUNT_MISSING_IDENTITY)
    }

    "reserve cameoId for new identity" in {
      val path = basePath + "/account/check"
      val json = Json.obj("cameoId" -> newIdentityCameoId)

      val req = FakeRequest(POST, path).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "reservationSecret").asOpt[String] must beSome
      regSec = (data \ "reservationSecret").as[String]
      1 === 1
    }

    "refuse to reserve loginName of other account" in {
      val path = basePath + "/account/check"
      val json = Json.obj("cameoId" -> loginExisting)

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((login + ":" + pass).getBytes)

      val req = FakeRequest(POST, path).withJsonBody(json).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != 232) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(232)
    }

    "allow reservation of own loginName as cameoId" in {
      val path = basePath + "/account/check"
      val json = Json.obj("cameoId" -> login)

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((login + ":" + pass).getBytes)

      val req = FakeRequest(POST, path).withJsonBody(json).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "reservationSecret").asOpt[String] must beSome
      regSec2 = (data \ "reservationSecret").as[String]
      1 === 1
    }

    "add identity using basic auth" in {
      val path = basePath + "/identity/initial"
      val json = Json.obj("displayName" -> newIdentityDisplayName, "phoneNumber" -> newIdentityTel, "email" -> newIdentityEmail, "cameoId" -> newIdentityCameoId, "reservationSecret" -> regSec)

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((login + ":" + pass).getBytes)

      val req = FakeRequest(POST, path).withJsonBody(json).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "identity").asOpt[JsObject] must beSome
      (data \ "token" \ "token").asOpt[String] must beSome

      val identity = (data \ "identity").as[JsObject]
      (identity \ "id").asOpt[String] must beSome
      newIdentityId = (identity \ "id").as[String]
      (identity \ "userKey").asOpt[String] must beSome
      (identity \ "cameoId").asOpt[String] must beSome(newIdentityCameoId + "@" + domain)
      (identity \ "email" \ "value").asOpt[String] must beSome(newIdentityEmail)
      (identity \ "phoneNumber" \ "value").asOpt[String] must beSome(newIdentityTel)
      (identity \ "displayName").asOpt[String] must beSome(newIdentityDisplayName)
      (identity \ "avatar").asOpt[String] must beSome
      (identity \ "publicKeys").asOpt[Seq[JsObject]] must beSome
    }

    "refuse to add another identity using basic auth" in {
      val path = basePath + "/identity/initial"
      val json = Json.obj("displayName" -> newIdentityDisplayName, "phoneNumber" -> newIdentityTel, "email" -> newIdentityEmail, "cameoId" -> newIdentityCameoId, "reservationSecret" -> regSec2)

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((login + ":" + pass).getBytes)

      val req = FakeRequest(POST, path).withJsonBody(json).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != BAD_REQUEST) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(BAD_REQUEST)

      (contentAsJson(res) \ "error").as[String] must contain("already has")
    }

    "Return a token" in {
      val path = basePath + "/token"

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((login + ":" + pass).getBytes)

      val req = FakeRequest(GET, path).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]
      val tokenOpt = (data \ "token").asOpt[String]
      if (tokenOpt.isDefined) {
        token = tokenOpt.get
      }

      tokenOpt must beSome
    }

    "account should contain new identity" in {
      val path = basePath + "/account"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]
      (data \ "identities").asOpt[Seq[JsObject]] must beSome
      (data \ "identities").as[Seq[JsObject]].length must beEqualTo(1)

      val identity = (data \ "identities")(0).as[JsObject]
      (identity \ "id").asOpt[String] must beSome(newIdentityId)
      (identity \ "userKey").asOpt[String] must beSome
      (identity \ "cameoId").asOpt[String] must beSome(newIdentityCameoId + "@" + domain)
      (identity \ "email" \ "value").asOpt[String] must beSome(newIdentityEmail)
      (identity \ "phoneNumber" \ "value").asOpt[String] must beSome(newIdentityTel)
      (identity \ "displayName").asOpt[String] must beSome(newIdentityDisplayName)
      (identity \ "avatar").asOpt[String] must beSome
      (identity \ "publicKeys").asOpt[Seq[JsObject]] must beSome
    }

    "Return a token and ignore capitalization of loginName" in {
      val path = basePath + "/token"

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((login.toUpperCase + ":" + pass).getBytes)

      val req = FakeRequest(GET, path).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]
      (data \ "token").asOpt[String] must beSome
    }

    "Registration should be marked as incomplete" in {
      val data = getData(executeRequest(GET, "/account", OK, Some(token)))
      (data \ "registrationIncomplete").asOpt[Boolean] must beSome(true)
    }

    "Mark registration complete" in {
      val body = Json.obj("registrationIncomplete" -> false)
      checkOk(executeRequest(PUT, "/account", OK, Some(token), Some(body)))
    }

    "Registration should now be marked as completed" in {
      val data = getData(executeRequest(GET, "/account", OK, Some(token)))
      (data \ "registrationIncomplete").asOpt[Boolean] must beSome(false)
    }

    var fileId = ""
    "automatically create avatar for new identity" in {
      val path = basePath + "/identity"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "avatar").asOpt[String] must beSome
      fileId = (data \ "avatar").as[String]
      1 === 1
    }

    "check that avatar file meta exist" in {

      val path = basePath + "/file/" + fileId

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome(fileId)
      (data \ "chunks").asOpt[Seq[Int]] must beSome
      (data \ "chunks")(0).asOpt[Int] must beSome(0)
      (data \ "chunks")(1).asOpt[Int] must beNone
      (data \ "fileName").asOpt[String] must beSome("avatar.png")
      (data \ "fileSize").asOpt[Int] must beSome
      (data \ "fileType").asOpt[String] must beSome("image/png")
    }

    "check that avatar file chunk exists" in {
      val path = basePath + "/file/" + fileId + "/" + 0

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
      val raw = contentAsBytes(res)

      raw.length must beGreaterThan(100)
    }

    "automatically add support as contact" in {
      val path = basePath + "/contacts"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(1)

      (data(0) \ "identityId").asOpt[String] must beEqualTo(Play.configuration.getString("support.contact.identityId"))
    }

    "automatically add talk with support" in {
      val path = basePath + "/conversations"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]
      val conversations = (data \ "conversations").as[Seq[JsObject]]

      conversations.length must beEqualTo(1)
      (conversations(0) \ "subject").asOpt[String] must beEqualTo(Play.configuration.getString("support.conversation.subject"))

      val message = (conversations(0) \ "messages")(0).as[JsObject]
      (message \ "plain" \ "text").asOpt[String] must beEqualTo(Play.configuration.getString("support.conversation.body"))
    }

    var externalToken = ""
    "get purl object for external user" in {

      val path = basePath + "/purl/" + purlExtern2

      val req = FakeRequest(GET, path)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      externalToken = (data \ "token").as[String]

      1 === 1
    }

    var regSecExternal = ""
    "Reserve Login for external user" in {
      val path = basePath + "/account/check"
      val json = Json.obj("loginName" -> loginExternal)

      val req = FakeRequest(POST, path).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      regSecExternal = (data \ "reservationSecret").as[String]

      1 === 1
    }

    "register user with token of external user" in {
      val path = basePathV2 + "/account"
      val json = createUser(loginExternal, pass) ++
        Json.obj("reservationSecret" -> regSecExternal)

      val req = FakeRequest(POST, path).withJsonBody(json).withHeaders(tokenHeader(externalToken))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      val identity = (data \ "identities")(0).as[JsObject]

      (identity \ "id").asOpt[String] must beSome(purlExtern2IdentitityId)
      (identity \ "phoneNumber" \ "value").asOpt[String] must beNone
      (identity \ "email" \ "value").asOpt[String] must beNone
      (identity \ "displayName").asOpt[String] must beNone
    }

    var purlExternIdentityToken = ""
    "get token of new account" in {
      val path = basePath + "/token"

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((loginExternal + ":" + pass).getBytes)

      val req = FakeRequest(GET, path).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      (contentAsJson(res) \ "data" \ "token").asOpt[String] must beSome
      purlExternIdentityToken = (contentAsJson(res) \ "data" \ "token").as[String]

      1 === 1
    }

    "get identity of new account" in {
      val path = basePath + "/identity/" + purlExtern2IdentitityId

      val req = FakeRequest(GET, path)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome(purlExtern2IdentitityId)
      (data \ "cameoId").asOpt[String] must beSome(loginExternal + "@" + domain)
      (data \ "avatar").asOpt[String] must beSome
      (data \ "displayName").asOpt[String] must beNone
      (data \ "email").asOpt[JsObject] must beNone
      (data \ "phoneNumber").asOpt[JsObject] must beNone
    }

    "identity should have sender as contact" in {

      val path = basePath + "/contacts"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(purlExternIdentityToken))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(2)

      data.find(js => (js \ "identityId").asOpt[String].equals(Some(identityExisting2))) must beSome
    }

    "identity should have support contact" in {

      val path = basePath + "/contacts"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(purlExternIdentityToken))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[Seq[JsObject]]

      data.length must beEqualTo(2)

      data.find(js => (js \ "identityId").asOpt[String].equals(Play.configuration.getString("support.contact.identityId"))) must beSome
    }

    "identity should have support conversation" in {

      val path = basePath + "/conversations"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(purlExternIdentityToken))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data" \ "conversations").as[Seq[JsObject]]

      data.length must beEqualTo(2)
    }

    var regSec3 = ""
    "Reserve another login" in {
      val path = basePath + "/account/check"
      val json = Json.obj("loginName" -> (loginExternal + "moep"))

      val req = FakeRequest(POST, path).withJsonBody(json)
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      regSec3 = (data \ "reservationSecret").as[String]

      1 === 1
    }

    "refuse to register with token of internal user" in {
      val path = basePath + "/account"
      val json = createUser(loginExternal, pass) ++
        Json.obj("reservationSecret" -> regSec3)

      val req = FakeRequest(POST, path).withJsonBody(json).withHeaders(tokenHeader(tokenExisting))
      val res = route(req).get

      status(res) must equalTo(BAD_REQUEST)
    }

    val newPhoneNumber = "+49123456"
    val newEmail = "devnull4@cameo.io"
    val newPassword = "asdfasdfasdf"

    "update phoneNumber and email of account" in {
      val path = basePath + "/account"
      val json = Json.obj("phoneNumber" -> newPhoneNumber, "email" -> newEmail)

      val req = FakeRequest(PUT, path).withJsonBody(json).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "account should contain new values" in {
      val path = basePath + "/account"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome
      (data \ "loginName").asOpt[String] must beSome
      (data \ "identities").asOpt[Seq[JsObject]] must beSome
      (data \ "email" \ "value").asOpt[String] must beSome(newEmail)
      (data \ "phoneNumber" \ "value").asOpt[String] must beSome(newPhoneNumber)
    }

    "remove phoneNumber and email from account" in {
      val path = basePath + "/account"
      val json = Json.obj("phoneNumber" -> "", "email" -> "")

      val req = FakeRequest(PUT, path).withJsonBody(json).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "phoneNumber and email should be removed from account" in {
      val path = basePath + "/account"

      val req = FakeRequest(GET, path).withHeaders(tokenHeader(token))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)

      val data = (contentAsJson(res) \ "data").as[JsObject]

      (data \ "id").asOpt[String] must beSome
      (data \ "loginName").asOpt[String] must beSome
      (data \ "identities").asOpt[Seq[JsObject]] must beSome
      (data \ "email").asOpt[JsValue] must beNone
      (data \ "phoneNumber").asOpt[JsValue] must beNone
    }

    invalidPhoneNumbers.map {
      invalidPhoneNumber =>
        "return error code when adding invalid phonenumber to account: " + invalidPhoneNumber in {
          val body = Json.obj("phoneNumber" -> invalidPhoneNumber)
          val response = executeRequest(PUT, "/account", BAD_REQUEST, Some(token), Some(body))
          checkErrorCodes(response, ErrorCodes.PHONENUMBER_INVALID)
        }

        "return error code when adding invalid phonenumber with valid email to account: " + invalidPhoneNumber in {
          val body = Json.obj("phoneNumber" -> invalidPhoneNumber, "email" -> validEmails.head)
          val response = executeRequest(PUT, "/account", BAD_REQUEST, Some(token), Some(body))
          checkErrorCodes(response, ErrorCodes.PHONENUMBER_INVALID)
        }
    }

    invalidEmails.map {
      invalidEmail =>
        "return error code when adding invalid email to account: " + invalidEmail in {
          val body = Json.obj("email" -> invalidEmail)
          val response = executeRequest(PUT, "/account", BAD_REQUEST, Some(token), Some(body))
          checkErrorCodes(response, ErrorCodes.EMAIL_INVALID)
        }

        "return error code when adding invalid email with valid phonenumber to account: " + invalidEmail in {
          val body = Json.obj("email" -> invalidEmail, "phoneNumber" -> validPhoneNumbers.head._2)
          val response = executeRequest(PUT, "/account", BAD_REQUEST, Some(token), Some(body))
          checkErrorCodes(response, ErrorCodes.EMAIL_INVALID)
        }
    }

    for {
      invalidPhoneNumber <- invalidPhoneNumbers
      invalidEmail <- invalidEmails
    } yield {
      "return error code when adding invalid phonenumber and email to account: " + invalidPhoneNumber + " + " + invalidEmail in {
        val body = Json.obj("phoneNumber" -> invalidPhoneNumbers.head, "email" -> invalidEmails.head)
        val response = executeRequest(PUT, "/account", BAD_REQUEST, Some(token), Some(body))
        checkErrorCodes(response, ErrorCodes.PHONENUMBER_INVALID ++ ErrorCodes.EMAIL_INVALID)
      }
    }

    "get token with old password" in {
      val path = basePath + "/token"

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((loginExisting2 + ":" + password).getBytes)

      val req = FakeRequest(GET, path).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "refuse to update account password without old password" in {
      val path = basePath + "/account"
      val json = Json.obj("password" -> newPassword)

      val req = FakeRequest(PUT, path).withJsonBody(json).withHeaders(tokenHeader(tokenExisting2))
      val res = route(req).get

      if (status(res) != BAD_REQUEST) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(BAD_REQUEST)
    }

    "refuse to update account password with invalid old password" in {
      val path = basePath + "/account"
      val json = Json.obj("password" -> newPassword, "oldPassword" -> "oldMeop")

      val req = FakeRequest(PUT, path).withJsonBody(json).withHeaders(tokenHeader(tokenExisting2))
      val res = route(req).get

      if (status(res) != BAD_REQUEST) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(BAD_REQUEST)
    }

    "update account password with valid old password" in {
      val path = basePath + "/account"
      val json = Json.obj("password" -> newPassword, "oldPassword" -> password)

      val req = FakeRequest(PUT, path).withJsonBody(json).withHeaders(tokenHeader(tokenExisting2))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }

    "refuse login with old password" in {
      val path = basePath + "/token"

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((loginExisting2 + ":" + password).getBytes)

      val req = FakeRequest(GET, path).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != UNAUTHORIZED) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(UNAUTHORIZED)

    }

    "allow login with new password" in {
      val path = basePath + "/token"

      val auth = "Basic " + new sun.misc.BASE64Encoder().encode((loginExisting2 + ":" + newPassword).getBytes)

      val req = FakeRequest(GET, path).withHeaders(("Authorization", auth))
      val res = route(req).get

      if (status(res) != OK) {
        Logger.error("Response: " + contentAsString(res))
      }
      status(res) must equalTo(OK)
    }
  }
}