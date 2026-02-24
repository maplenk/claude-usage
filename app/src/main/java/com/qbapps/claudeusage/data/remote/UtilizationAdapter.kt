package com.qbapps.claudeusage.data.remote

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * Gson TypeAdapter that handles the utilization field which can arrive as
 * an Int (72), a Double (72.5), or a String ("72.5") from the API.
 */
class UtilizationAdapter : TypeAdapter<Double>() {

    override fun write(out: JsonWriter, value: Double?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value)
        }
    }

    override fun read(reader: JsonReader): Double {
        return when (reader.peek()) {
            JsonToken.NUMBER -> reader.nextDouble()
            JsonToken.STRING -> {
                val raw = reader.nextString()
                raw.toDoubleOrNull() ?: 0.0
            }
            JsonToken.NULL -> {
                reader.nextNull()
                0.0
            }
            else -> {
                reader.skipValue()
                0.0
            }
        }
    }
}
