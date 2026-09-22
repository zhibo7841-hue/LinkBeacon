package com.networktoolbox.feature.webdiagnostics.history

internal sealed interface HistoryJsonValue
internal data class HistoryJsonObject(val values: Map<String, HistoryJsonValue>) : HistoryJsonValue
internal data class HistoryJsonArray(val values: List<HistoryJsonValue>) : HistoryJsonValue
internal data class HistoryJsonString(val value: String) : HistoryJsonValue
internal data class HistoryJsonNumber(val value: String) : HistoryJsonValue
internal data class HistoryJsonBoolean(val value: Boolean) : HistoryJsonValue
internal data object HistoryJsonNull : HistoryJsonValue

internal class HistoryJsonParser(private val source: String) {
    private var index = 0

    fun parse(): HistoryJsonValue {
        require(source.length <= MAX_INPUT_LENGTH) { "History payload is too large." }
        val result = parseValue(0)
        skipWhitespace()
        require(index == source.length) { "Unexpected JSON data." }
        return result
    }

    private fun parseValue(depth: Int): HistoryJsonValue {
        require(depth <= MAX_DEPTH) { "JSON nesting is too deep." }
        skipWhitespace()
        require(index < source.length) { "Unexpected end of JSON." }
        return when (source[index]) {
            '{' -> parseObject(depth + 1)
            '[' -> parseArray(depth + 1)
            '"' -> HistoryJsonString(parseString())
            't' -> parseLiteral("true", HistoryJsonBoolean(true))
            'f' -> parseLiteral("false", HistoryJsonBoolean(false))
            'n' -> parseLiteral("null", HistoryJsonNull)
            '-', in '0'..'9' -> HistoryJsonNumber(parseNumber())
            else -> error("Invalid JSON value.")
        }
    }

    private fun parseObject(depth: Int): HistoryJsonObject {
        expect('{')
        skipWhitespace()
        val values = linkedMapOf<String, HistoryJsonValue>()
        if (consumeIf('}')) return HistoryJsonObject(values)
        while (true) {
            skipWhitespace()
            require(peek() == '"') { "Object key must be a string." }
            val key = parseString()
            skipWhitespace()
            expect(':')
            values[key] = parseValue(depth)
            skipWhitespace()
            when {
                consumeIf('}') -> return HistoryJsonObject(values)
                consumeIf(',') -> Unit
                else -> error("Invalid JSON object separator.")
            }
        }
    }

    private fun parseArray(depth: Int): HistoryJsonArray {
        expect('[')
        skipWhitespace()
        val values = mutableListOf<HistoryJsonValue>()
        if (consumeIf(']')) return HistoryJsonArray(values)
        while (true) {
            values += parseValue(depth)
            skipWhitespace()
            when {
                consumeIf(']') -> return HistoryJsonArray(values)
                consumeIf(',') -> Unit
                else -> error("Invalid JSON array separator.")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (index < source.length) {
            when (val character = source[index++]) {
                '"' -> return result.toString()
                '\\' -> {
                    require(index < source.length) { "Invalid JSON escape." }
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            require(index + 4 <= source.length) { "Invalid unicode escape." }
                            result.append(source.substring(index, index + 4).toInt(16).toChar())
                            index += 4
                        }
                        else -> error("Invalid JSON escape.")
                    }
                }
                else -> {
                    require(character.code >= 0x20) { "Invalid control character." }
                    result.append(character)
                }
            }
        }
        error("Unterminated JSON string.")
    }

    private fun parseNumber(): String {
        val start = index
        consumeIf('-')
        require(index < source.length) { "Invalid JSON number." }
        if (!consumeIf('0')) require(consumeDigits()) { "Invalid JSON number." }
        if (consumeIf('.')) require(consumeDigits()) { "Invalid JSON number." }
        if (index < source.length && source[index] in "eE") {
            index++
            if (index < source.length && source[index] in "+-") index++
            require(consumeDigits()) { "Invalid JSON number." }
        }
        return source.substring(start, index)
    }

    private fun consumeDigits(): Boolean {
        val start = index
        while (index < source.length && source[index].isDigit()) index++
        return index > start
    }

    private fun parseLiteral(expected: String, value: HistoryJsonValue): HistoryJsonValue {
        require(source.startsWith(expected, index)) { "Invalid JSON literal." }
        index += expected.length
        return value
    }

    private fun expect(value: Char) = require(consumeIf(value)) { "Expected '$value'." }
    private fun consumeIf(value: Char): Boolean = if (source.getOrNull(index) == value) {
        index++
        true
    } else false
    private fun peek(): Char = source.getOrNull(index) ?: error("Unexpected end of JSON.")
    private fun skipWhitespace() { while (index < source.length && source[index].isWhitespace()) index++ }

    private companion object {
        const val MAX_DEPTH = 32
        const val MAX_INPUT_LENGTH = 1_000_000
    }
}

internal fun historyJsonObject(vararg fields: Pair<String, String>): String =
    fields.joinToString(prefix = "{", postfix = "}") { (key, value) -> "${historyJsonString(key)}:$value" }

internal fun historyJsonArray(values: Iterable<String>): String = values.joinToString(prefix = "[", postfix = "]")
internal fun historyJsonStringArray(values: Iterable<String>): String = historyJsonArray(values.map(::historyJsonString))
internal fun historyJsonNullable(value: String?): String = value?.let(::historyJsonString) ?: "null"
internal fun historyJsonNullable(value: Long?): String = value?.toString() ?: "null"
internal fun historyJsonNullable(value: Int?): String = value?.toString() ?: "null"
internal fun historyJsonNullable(value: Boolean?): String = value?.toString() ?: "null"

internal fun historyJsonString(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
        }
    }
    append('"')
}

internal fun HistoryJsonObject.string(key: String): String? = (values[key] as? HistoryJsonString)?.value
internal fun HistoryJsonObject.long(key: String): Long? = (values[key] as? HistoryJsonNumber)?.value?.toLongOrNull()
internal fun HistoryJsonObject.int(key: String): Int? = long(key)?.toInt()
internal fun HistoryJsonObject.boolean(key: String): Boolean? = (values[key] as? HistoryJsonBoolean)?.value
internal fun HistoryJsonObject.obj(key: String): HistoryJsonObject? = values[key] as? HistoryJsonObject
internal fun HistoryJsonObject.array(key: String): HistoryJsonArray? = values[key] as? HistoryJsonArray
internal fun HistoryJsonObject.stringList(key: String): List<String>? =
    array(key)?.values?.map { (it as? HistoryJsonString)?.value ?: return null }
internal inline fun <reified T : Enum<T>> HistoryJsonObject.enum(key: String): T? =
    string(key)?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
