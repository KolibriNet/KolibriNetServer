package actors

import akka.actor.Actor
import com.puship.{PushipUtil, CoreApi, Credentials}
import play.api.{Logger, Play}
import services.{ PushEvent, EventDefinition }
import play.api.Play.current

import scala.collection.immutable.HashSet

/**
 * User: Björn Reimer
 * Date: 02.09.14
 * Time: 15:11
 */

case class PushNotification(message: String,
                            deviceToken: String)

class PushNotificationActor extends Actor {
  def receive = {
    case PushNotification(message, deviceToken) =>

      val username = Play.configuration.getString("pushIp.username")
      val password = Play.configuration.getString("pushIp.password")
      val appId = Play.configuration.getString("pushIp.appId")

      Logger.debug(username + ":" + password + ":" + appId)

      username.isEmpty || password.isEmpty || appId.isEmpty match {
        case true =>
          Logger.warn("No PushIp credentials")
        case false =>
          val credentials: Credentials = new Credentials(username.get, password.get)
          val coreApi: CoreApi = new CoreApi(appId.get, credentials)
//          coreApi.EnableDebug = true
          PushipUtil.SetTimeZone("Europe/Berlin")

          val javaMap = new java.util.HashMap[String, AnyRef]()
          javaMap.put("Message", message)

          val javaSet= new java.util.HashSet[String]()
          javaSet.add(deviceToken)

          javaMap.put("Devices", javaSet)
//
          val response = coreApi.SendPushMessageByDevice(javaMap)
          Logger.info("Puship response: " + response)


      }
  }
}
