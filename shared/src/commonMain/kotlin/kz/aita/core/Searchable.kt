package kz.aita.core

@Suppress("UNCHECKED_CAST")
fun <T : Searchable> List<Searchable>.search(query: String): Pair<List<T>, Boolean> {
  val unique = filter {
    it.searchUnique(query)
  }

  if (unique.size == 1)
    return unique.map { it as T } to true

  val exact = filter {
    it.searchExact(query)
  }
  val contains = filter {
    it.searchContains(query) && !exact.contains(it)
  }

  return mutableListOf<Searchable>()
    .apply {
      addAll(exact)
      addAll(contains)
    }
    .toList()
    .map { it as T } to false
}

interface Searchable {

  fun searchExact(query: String): Boolean
  fun searchContains(query: String): Boolean

  fun searchUnique(query: String): Boolean {
    return false
  }
}
