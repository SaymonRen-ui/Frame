package com.saymon.frame.data

/** Произведение: папка в библиотеке. */
data class Title(
    /** URI папки строкой — стабильный id. */
    val id: String,
    val name: String,
)

/** Глава: CBZ-файл. */
data class Chapter(
    val titleId: String,
    /** URI документа строкой. */
    val uri: String,
    val name: String,
)

/** Прогресс главы + запись истории. */
data class Progress(
    val titleId: String,
    val titleName: String,
    val chapterUri: String,
    val chapterName: String,
    val page: Int,
    val pages: Int,
    val time: Long,
)

/**
 * Естественная сортировка: «2» < «10», регистр не важен.
 * Главы вида «Глава 311.5» ложатся правильно.
 */
object NaturalSort : Comparator<String> {
    private val NUM = Regex("""\d+(\.\d+)?""")

    private fun tokens(s: String): List<Any> {
        val out = mutableListOf<Any>()
        var i = 0
        while (i < s.length) {
            val m = NUM.matchAt(s, i)
            if (m != null) {
                out.add(m.value.toDouble())
                i = m.range.last + 1
            } else {
                var j = i + 1
                while (j < s.length && NUM.matchAt(s, j) == null) j++
                out.add(s.substring(i, j).lowercase())
                i = j
            }
        }
        return out
    }

    override fun compare(a: String, b: String): Int {
        val ta = tokens(a)
        val tb = tokens(b)
        for (i in 0 until minOf(ta.size, tb.size)) {
            val x = ta[i]
            val y = tb[i]
            val c = when {
                x is Double && y is Double -> x.compareTo(y)
                x is String && y is String -> x.compareTo(y)
                x is Double -> -1
                else -> 1
            }
            if (c != 0) return c
        }
        return ta.size.compareTo(tb.size)
    }
}
