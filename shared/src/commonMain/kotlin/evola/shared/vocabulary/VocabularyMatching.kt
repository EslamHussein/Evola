package evola.shared.vocabulary

/** Typed-recall validation (01_PRODUCT_SPEC.md §1.8): "case-insensitive match, tolerant of a
 * single-character edit distance (don't require exact match)." */
fun isTolerantMatch(expected: String, actual: String): Boolean {
    val a = expected.trim().lowercase()
    val b = actual.trim().lowercase()
    if (a == b) return true
    return levenshteinDistance(a, b, maxDistance = 1) <= 1
}

/** Capped Levenshtein distance - stops early once it's clear the true distance exceeds
 * [maxDistance], since callers here only ever care about "is it <= 1." */
internal fun levenshteinDistance(a: String, b: String, maxDistance: Int): Int {
    if (kotlin.math.abs(a.length - b.length) > maxDistance) return maxDistance + 1

    var previousRow = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val currentRow = IntArray(b.length + 1)
        currentRow[0] = i
        for (j in 1..b.length) {
            currentRow[j] = if (a[i - 1] == b[j - 1]) {
                previousRow[j - 1]
            } else {
                1 + minOf(previousRow[j], currentRow[j - 1], previousRow[j - 1])
            }
        }
        previousRow = currentRow
    }
    return previousRow[b.length]
}
