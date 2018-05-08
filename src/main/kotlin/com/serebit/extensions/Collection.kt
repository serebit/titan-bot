@file:JvmName("CollectionExtensions")

package com.serebit.extensions

import java.util.*

private val random = Random()

internal fun <T> List<T>.randomEntry() = if (isNotEmpty()) {
    get(random.nextInt(size))
} else null

inline fun <T> Iterable<T>.chunkedBy(
    size: Int,
    maxChunkSize: Int = Int.MAX_VALUE,
    transform: (T) -> Int
): List<List<T>> {
    val list = mutableListOf(mutableListOf<T>())
    var accumulator = 0
    zip(map(transform)).forEach { (item, itemSize) ->
        when {
            accumulator + itemSize <= size && list.last().size < maxChunkSize -> {
                accumulator += itemSize
                list.last().add(item)
            }
            itemSize <= size -> {
                accumulator = itemSize
                list.add(mutableListOf(item))
            }
            else -> {
                accumulator = 0
                list.add(mutableListOf())
            }
        }
    }
    return list.toList()
}
