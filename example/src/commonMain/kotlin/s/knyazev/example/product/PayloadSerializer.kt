package com.github.knyazevs.korm.example.product

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object JsonElementSerializer : KSerializer<JsonElement> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("JsonElement", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: JsonElement) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): JsonElement {
        return Json.parseToJsonElement(decoder.decodeString())
    }
}
