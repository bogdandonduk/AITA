package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
sealed interface StoreJobDataModel {
  
  data object Cashier: StoreJobDataModel
  
  data object WarehouseManager: StoreJobDataModel
  
  data object Administrator: StoreJobDataModel
  
  fun serialize(): String {
    return when (this) {
      is Cashier -> "Cashier"
      is WarehouseManager -> "WarehouseManager"
      is Administrator -> "Administrator"
    }
  }

  companion object {
    fun deserialize(serialized: String): StoreJobDataModel {
      return when (serialized) {
        "Cashier" -> Cashier
        "WarehouseManager" -> WarehouseManager
        "Administrator" -> Administrator
        else -> throw IllegalStateException("Must be Cashier or WarehouseManager or Administrator")
      }
    }
  }
}
