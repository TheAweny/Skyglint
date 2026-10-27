package dev.skyglint.features.elections

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import java.io.StringReader
import dev.skyglint.core.ElectionVoteCandidate
import java.util.Collections
import java.util.Locale

/** Parse a copied public API response. HTTP and scheduling belong to the adapter. */
object ElectionApiPayload {
    data class Payload(val year: Int, val candidates: List<ElectionVoteCandidate>)
    private val name = Regex("[A-Za-z][A-Za-z -]{0,39}")
    private val integer = Regex("[0-9]{1,19}")

    /** A valid success response can explicitly have no ongoing election; malformed data cannot. */
    fun isClosed(json: String): Boolean {
        if (json.length > 131_072) return false
        return try {
            val root = strictJson(json).takeIf { it.isJsonObject }?.asJsonObject ?: return false
            val success = root.get("success")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return false
            success.isBoolean && success.asBoolean && (!root.has("current") || root.get("current").isJsonNull)
        } catch (_: RuntimeException) { false }
    }

    fun parse(json: String): Payload? {
        if (json.length > 131_072) return null
        return try {
            val root = strictJson(json).takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val success = root.get("success")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
            if (!success.isBoolean || !success.asBoolean) return null
            // mayor.election describes previous results and is deliberately never a fallback.
            val current = root.get("current")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val year = unsignedLong(current.get("year"))?.takeIf { it in 1L..100_000L }?.toInt() ?: return null
            val rows = current.get("candidates")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
            if (rows.size() !in 2..12) return null
            val candidates = rows.map { row ->
                val candidate = row.takeIf { it.isJsonObject }?.asJsonObject ?: return null
                val candidateName = candidate.get("name")?.takeIf { it.isJsonPrimitive }
                    ?.asJsonPrimitive?.takeIf { it.isString }?.asString ?: return null
                val votes = unsignedLong(candidate.get("votes")) ?: return null
                ElectionVoteCandidate(candidateName, votes)
            }
            validated(year, candidates)
        } catch (_: RuntimeException) { null }
    }

    /** Domain observations also pass these checks; callers cannot bypass JSON validation. */
    fun validated(year: Int, candidates: List<ElectionVoteCandidate>): Payload? {
        if (year !in 1..100_000 || candidates.size !in 2..12 || candidates.any { !name.matches(it.name) || it.votes < 0 }) return null
        if (candidates.map { it.name.lowercase(Locale.ROOT) }.toSet().size != candidates.size) return null
        var total = 0L
        for (candidate in candidates) {
            if (candidate.votes > Long.MAX_VALUE - total) return null
            total += candidate.votes
        }
        if (total == 0L) return null
        return Payload(year, Collections.unmodifiableList(candidates.sortedWith(
            compareByDescending<ElectionVoteCandidate> { it.votes }.thenBy { it.name })))
    }

    private fun unsignedLong(element: JsonElement?): Long? {
        val number = element?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber } ?: return null
        return number.toString().takeIf(integer::matches)?.toLongOrNull()
    }

    private fun strictJson(json: String): JsonElement = JsonReader(StringReader(json)).use { reader ->
        reader.strictness = Strictness.STRICT
        JsonParser.parseReader(reader).also {
            require(reader.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT)
        }
    }
}
