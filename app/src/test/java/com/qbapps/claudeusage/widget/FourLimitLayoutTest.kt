package com.qbapps.claudeusage.widget

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FourLimitLayoutTest {

    @Test
    fun `tall widget keeps the roomy tier`() {
        val layout = fourLimitLayout(width = 340.dp, height = 200.dp)

        assertEquals(20.dp, layout.rowHeight)
        assertEquals(9.dp, layout.rowGap)
        assertEquals(16.dp, layout.verticalPadding)
        assertEquals(14.dp, layout.footerHeight)
        assertEquals(14.sp, layout.valueFontSize)
        assertTrue(layout.showFooter)
    }

    @Test
    fun `compact height tier is unchanged between 100dp and 150dp`() {
        val layout = fourLimitLayout(width = 340.dp, height = 120.dp)

        assertEquals(16.dp, layout.rowHeight)
        assertEquals(5.dp, layout.rowGap)
        assertEquals(8.dp, layout.verticalPadding)
        assertEquals(12.dp, layout.footerHeight)
        assertEquals(14.sp, layout.valueFontSize)
        assertTrue(layout.showFooter)
    }

    @Test
    fun `ultra compact height tightens rows and drops the footer`() {
        val layout = fourLimitLayout(width = 340.dp, height = 90.dp)

        assertEquals(14.dp, layout.rowHeight)
        assertEquals(2.dp, layout.rowGap)
        assertEquals(4.dp, layout.verticalPadding)
        assertEquals(0.dp, layout.footerHeight)
        assertEquals(12.sp, layout.valueFontSize)
        assertEquals(false, layout.showFooter)
    }

    @Test
    fun `ultra compact rows fit inside the declared 70dp minimum height`() {
        val layout = fourLimitLayout(width = 340.dp, height = 70.dp)
        val required = (layout.rowHeight * 4) + (layout.rowGap * 3) +
            (layout.verticalPadding * 2) + layout.footerHeight

        assertEquals(70.dp, required)
    }
}
