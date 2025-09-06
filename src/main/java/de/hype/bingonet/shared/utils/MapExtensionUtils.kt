package de.hype.bingonet.shared.utils

fun <K, V> MutableMap<K, V>.modifyValues(transform: (Map.Entry<K, V>) -> V) {
    return this.entries.forEach { it.setValue(transform(it)) }
}

fun <K, V> MutableMap<K, V>.modifyKeys(transform: (Map.Entry<K, V>) -> K) {
    entries.forEach {
        val newKey = transform(it)
        if (newKey == it.key) return@forEach // No change in key, skip
        this.remove(it.key)
        this[newKey] = it.value
    }
}

/**
 * Returns a new HASH map without the first [int] entries.
 * If [int] is less than or equal to 0, returns the original map.
 */
fun <K, V> Map<K, V>.skip(int: Int): Map<K, V> {
    if (int <= 0) return this
    val result = _root_ide_package_.kotlin.collections.HashMap<K, V>()
    var count = 0
    for (entry in this.entries) {
        if (count >= int) {
            result[entry.key] = entry.value
        }
        count++
    }
    return result
}
