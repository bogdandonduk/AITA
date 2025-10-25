package kz.aita.server.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb

object GenericGoodsCategories: Table("generic_goods_categories") {
  val id = uuid("id").uniqueIndex()

  val typeIds = jsonb("type_ids", Json, ListSerializer(String.serializer()))
  val name = jsonb("name", Json, ListSerializer(LocalizedStringDataModel.serializer()))
  val quantityUnitId = text("quantity_unit_id")
  val imagePaths = jsonb("image_paths", Json, ListSerializer(StylizedDrawablePathsGroupDataModel.serializer()))

  override val primaryKey = PrimaryKey(id)
}