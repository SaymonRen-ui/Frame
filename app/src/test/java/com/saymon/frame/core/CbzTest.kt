package com.saymon.frame.core

import com.saymon.frame.data.NaturalSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CbzTest {
    @Test
    fun naturalSortChapters() {
        val chapters = listOf(
            "Глава 10.cbz", "Глава 2.cbz", "Глава 311.5.cbz",
            "Глава 1.cbz", "Глава 20.cbz")
        val sorted = chapters.sortedWith(NaturalSort)
        assertEquals(
            listOf("Глава 1.cbz", "Глава 2.cbz", "Глава 10.cbz",
                "Глава 20.cbz", "Глава 311.5.cbz"),
            sorted)
    }

    @Test
    fun naturalSortCaseAndNums() {
        val sorted = listOf("b10.jpg", "B2.jpg", "a1.jpg")
            .sortedWith(NaturalSort)
        assertEquals(listOf("a1.jpg", "B2.jpg", "b10.jpg"), sorted)
        assertTrue(NaturalSort.compare("001.jpg", "1.jpg") == 0 ||
            NaturalSort.compare("001.jpg", "02.jpg") < 0)
    }
}
