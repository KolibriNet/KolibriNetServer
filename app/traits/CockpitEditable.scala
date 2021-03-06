package traits

import controllers.cockpit.ListController.{ ListOptions, SelectedFilters }
import helper.JsonHelper._
import helper.MongoCollections._
import models.cockpit._
import play.api.Logger
import play.api.libs.json.{ JsObject, Json }
import play.modules.reactivemongo.json.BSONFormats._
import reactivemongo.core.commands._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * User: Björn Reimer
 * Date: 3/11/14
 * Time: 2:01 PM
 */

case class CockpitEditableDefinition(name: String,
                                     getList: ListOptions => Future[CockpitList],
                                     delete: (String) => Future[LastError],
                                     create: CockpitListElement,
                                     getAttributes: String => Future[Option[Seq[JsObject]]],
                                     update: (String, JsObject) => Future[Option[Boolean]])

trait CockpitEditable[A] extends Model[A] {

  def cockpitMapping: Seq[CockpitAttribute]

  def cockpitListFilters: Seq[CockpitListFilter]

  /**
   * Helper
   */
  def getTitles: Seq[String] = cockpitMapping.filter(_.getShowInList).map {
    _.getDisplayName
  }

  def getCockpitListElement(obj: A): CockpitListElement = {
    val js = Json.toJson(obj).as[JsObject]
    val id = (js \ "_id" \ "mongoId").as[String]
    val attributes: Map[String, Option[String]] = cockpitMapping.filter(_.getShowInList).zipWithIndex.map {
      case (atr, index) =>
        (index.toString, atr.getListString(js))
    }.toMap
    new CockpitListElement(id, attributes)
  }

  def getCockpitList(listOptions: ListOptions): Future[CockpitList] = {
    val filterJsons = listOptions.filter.map {
      case SelectedFilters(filterName, term) =>
        // get filter from list
        cockpitListFilters.find(_.filterName.equals(filterName)) match {
          case None            => Json.obj()
          case Some(filterDef) => filterDef.filterFunction(term)
        }
    }
    // convert them to Mongo Match
    val matches = filterJsons.map {
      js => Match(toBson(js).get)
    }

    // add limit and offset
    val pipeline: Seq[PipelineOperator] = matches ++
      Seq(
        Skip(listOptions.offset),
        Limit(listOptions.limit))

    val aggregationCommand = Aggregate(col.name, pipeline)

    mongoDB.command(aggregationCommand).map {
      res =>
        {
          val list = res.toSeq.map {
            bson =>
              Json.toJson(bson).as[A]
          }
          val elements = list.map(getCockpitListElement)
          new CockpitList(getTitles, elements, cockpitListFilters)
        }
    }
  }

  def getAttributes(id: String): Future[Option[Seq[JsObject]]] =
    find(id).map {
      _.map {
        obj =>
          val js = Json.toJson(obj).as[JsObject]

          // get edit json for each attribute
          cockpitMapping.map(_.getEditJson(js))
      }
    }

  def newCockpitListElement: CockpitListElement = {
    val obj = createDefault()
    col.insert(obj)
    getCockpitListElement(obj)
  }

  def updateElement(id: String, updateJs: JsObject): Future[Option[Boolean]] = {
    val transformer = cockpitMapping.map {
      _.getTransformer(updateJs)
    }.filter(_.isDefined).map(_.get)

    // get original object
    find(id).flatMap {
      case None => Future(None)
      case Some(obj) =>
        val originalJs = Json.toJson(obj)
        // apply transformers
        val updatedJs = transformer.foldLeft(originalJs)(
          (js, transformer) =>
            js.transform(transformer).getOrElse {
              Logger.error("transformer failed")
              Json.obj()
            }
        )
        // check if the new js can still be deserialized
        updatedJs.asOpt[A] match {
          case None =>
            Logger.error("could not deserialize after transform: " + updatedJs)
            Future(None)
          case Some(obj) =>
            // save to db
            save(Json.toJson(obj).as[JsObject]).map {
              lastError => Some(lastError.updatedExisting)
            }
        }

    }
  }

}
