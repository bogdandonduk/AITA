package kz.aita.server

import kz.aita.GoodsItemDataModel
import kz.aita.jsonBase
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb

/** The accepted edit survives a lost response, subsequent edits, and client restarts. */
internal object StockItemEditCommands : Table("stock_item_edit_commands") {
    val id = uuid("id")
    val storeId = uuid("store_id")
    val actorId = uuid("actor_id")
    val request = jsonb("request", jsonBase, GoodsItemDataModel.serializer())
    val result = jsonb("result", jsonBase, GoodsItemDataModel.serializer())
    override val primaryKey = PrimaryKey(id)
}
