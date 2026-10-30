package io.github.aedev.flow.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class FlowTypographyTest {
    private fun Typography.allRoles(): List<TextStyle> =
        listOf(
            displayLarge,
            displayMedium,
            displaySmall,
            headlineLarge,
            headlineMedium,
            headlineSmall,
            titleLarge,
            titleMedium,
            titleSmall,
            bodyLarge,
            bodyMedium,
            bodySmall,
            labelLarge,
            labelMedium,
            labelSmall,
            displayLargeEmphasized,
            displayMediumEmphasized,
            displaySmallEmphasized,
            headlineLargeEmphasized,
            headlineMediumEmphasized,
            headlineSmallEmphasized,
            titleLargeEmphasized,
            titleMediumEmphasized,
            titleSmallEmphasized,
            bodyLargeEmphasized,
            bodyMediumEmphasized,
            bodySmallEmphasized,
            labelLargeEmphasized,
            labelMediumEmphasized,
            labelSmallEmphasized,
        )

    @Test
    fun everyRoleAndItsEmphasizedFormUseTheChosenFamily() {
        val roles = flowTypography(FontFamily.Serif).allRoles()
        assertEquals(30, roles.size)
        roles.forEach { assertEquals(FontFamily.Serif, it.fontFamily) }
    }

    @Test
    fun displayAndHeadlineSmallKeepMaterialsMetrics() {
        val material = Typography()
        val flow = flowTypography(FlowFontFamily)
        assertEquals(material.displaySmall.copy(fontFamily = FlowFontFamily), flow.displaySmall)
        assertEquals(material.headlineSmall.copy(fontFamily = FlowFontFamily), flow.headlineSmall)
        assertEquals(36.sp, flow.displaySmall.fontSize)
        assertEquals(24.sp, flow.headlineSmall.fontSize)
    }

    @Test
    fun theSystemFontScaleKeepsTodaysValues() {
        val flow = flowTypography(FlowFontFamily)
        assertEquals(34.sp, flow.displayLarge.fontSize)
        assertEquals(FontWeight.Bold, flow.displayLarge.fontWeight)
        assertEquals(18.sp, flow.titleLarge.fontSize)
        assertEquals(0.25.sp, flow.bodyMedium.letterSpacing)
        assertEquals(11.sp, flow.labelSmall.fontSize)
        assertEquals(FontWeight.ExtraBold, flow.displayLargeEmphasized.fontWeight)
        assertEquals(FontWeight.Medium, flow.bodyLargeEmphasized.fontWeight)
        assertEquals(flow, Typography)
    }
}
