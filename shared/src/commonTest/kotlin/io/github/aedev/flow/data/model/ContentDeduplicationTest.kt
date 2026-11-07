package io.github.aedev.flow.data.model

import io.github.aedev.flow.testing.assertEquals
import kotlin.test.assertNotSame
import io.github.aedev.flow.testing.assertSame
import kotlin.test.Test

class ContentDeduplicationTest {

    @Test
    fun keepsTheFirstItemForEachStableKeyInSourceOrder() {
        val items = listOf(
            TestItem(id = "first", value = "original"),
            TestItem(id = "second", value = "other"),
            TestItem(id = "first", value = "duplicate")
        )

        val result = items.distinctByNonBlankKey(TestItem::id)

        assertEquals(
            listOf(
                TestItem(id = "first", value = "original"),
                TestItem(id = "second", value = "other")
            ),
            result
        )
    }

    @Test
    fun dropsItemsThatCannotProvideAStableKey() {
        val items = listOf(
            TestItem(id = "", value = "empty"),
            TestItem(id = "   ", value = "blank"),
            TestItem(id = "valid", value = "kept")
        )

        val result = items.distinctByNonBlankKey(TestItem::id)

        assertEquals(listOf(TestItem(id = "valid", value = "kept")), result)
    }

    @Test
    fun mergeKeepsExistingItemsAndAddsOnlyNewStableKeys() {
        val existing = listOf(
            TestItem(id = "first", value = "existing"),
            TestItem(id = "second", value = "existing")
        )
        val incoming = listOf(
            TestItem(id = "second", value = "duplicate"),
            TestItem(id = "third", value = "new")
        )

        val result = existing.mergeDistinctByNonBlankKey(incoming, TestItem::id)

        assertEquals(
            listOf(
                TestItem(id = "first", value = "existing"),
                TestItem(id = "second", value = "existing"),
                TestItem(id = "third", value = "new")
            ),
            result
        )
    }

    @Test
    fun orSelfNormalizationPreservesListIdentityWhenAlreadyValid() {
        val valid = listOf(
            TestItem(id = "first", value = "one"),
            TestItem(id = "second", value = "two")
        )
        val duplicated = valid + TestItem(id = "first", value = "duplicate")

        assertSame(valid, valid.distinctByNonBlankKeyOrSelf(TestItem::id))
        assertNotSame(duplicated, duplicated.distinctByNonBlankKeyOrSelf(TestItem::id))
    }

    @Test
    fun trackerRemovesDuplicatesWithinAndAcrossPagingLoads() {
        val tracker = DistinctKeyTracker()

        val firstPage = tracker.filter(
            listOf(
                TestItem(id = "first", value = "original"),
                TestItem(id = "first", value = "same-page duplicate"),
                TestItem(id = "second", value = "original")
            ),
            TestItem::id
        )
        val secondPage = tracker.filter(
            listOf(
                TestItem(id = "second", value = "previous-page duplicate"),
                TestItem(id = "third", value = "new")
            ),
            TestItem::id
        )

        assertEquals(listOf("first", "second"), firstPage.map(TestItem::id))
        assertEquals(listOf("third"), secondPage.map(TestItem::id))
    }

    private data class TestItem(
        val id: String,
        val value: String
    )
}
