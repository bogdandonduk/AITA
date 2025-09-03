package kz.aita.model.repository

import kz.aita.model.dataModel.response.GetStringsResponseDataModel

interface ConfigurationRepository {

  fun getStrings(): GetStringsResponseDataModel
}