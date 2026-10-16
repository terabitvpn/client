package ru.terabitvpn.core

import kotlinx.serialization.json.Json

val JsonX = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    explicitNulls = false
}

val JsonPretty = Json(JsonX) { prettyPrint = true }
