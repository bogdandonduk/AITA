package kz.aita

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
fun newDiagnosticId(): String = kotlin.uuid.Uuid.random().toString()
