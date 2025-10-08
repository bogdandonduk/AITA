package kz.aita.core

@Suppress("UNCHECKED_CAST")
fun <T : Searchable> List<Searchable>.search(query: String, vararg extraOperands: String): Pair<List<T>, Boolean> {
  singleOrNull {
    it.searchUnique(query, *extraOperands)
  }?.run {
    return map { it as T } to true
  }

  val exact = filter {
    it.searchExact(query, *extraOperands)
  }
  val contains = filter {
    it.searchContains(query, *extraOperands) && !exact.contains(it)
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

  val exactSearchOperands: List<String>
  val containsSearchOperands: List<String>
  val uniqueSearchOperands: List<String>


  fun searchExact(query: String, vararg extraOperands: String): Boolean {
    return exactSearchOperands.any { it.equals(query, true) }
        || extraOperands.any { it.equals(query, true) }
  }

  fun searchContains(query: String, vararg extraOperands: String): Boolean {
    return containsSearchOperands.any { it.equals(query, true) }
        || extraOperands.any { it.equals(query, true) }
  }

  fun searchUnique(query: String, vararg extraOperands: String): Boolean {
    return uniqueSearchOperands.all { it.equals(query, true) } && extraOperands.any { it.equals(query, true) }
  }
}
