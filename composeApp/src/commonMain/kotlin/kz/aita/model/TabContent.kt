package kz.aita.model

data class TabContent(
  val text: String,
  val onClick: ((Int) -> Unit)? = null
)