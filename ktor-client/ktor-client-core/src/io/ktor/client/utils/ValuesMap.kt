package io.ktor.client.utils

import io.ktor.util.*


fun ValuesMapBuilder.appendAll(valuesMap: ValuesMapBuilder): ValuesMapBuilder = apply {
    valuesMap.entries().forEach { (name, values) ->
        appendAll(name, values)
    }
}

fun valuesMapBuilderOf(builder: ValuesMapBuilder): ValuesMapBuilder =
        ValuesMapBuilder().appendAll(builder)

fun valuesOf(builder: ValuesMapBuilder): ValuesMap = valuesMapBuilderOf(builder).build()
