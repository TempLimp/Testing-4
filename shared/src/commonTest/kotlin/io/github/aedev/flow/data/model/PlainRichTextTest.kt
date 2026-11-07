package io.github.aedev.flow.data.model

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class PlainRichTextTest {
    @Test
    fun lineBreaksAndTextComeThroughUntouched() {
        val text = "Обзор смартфона Xiaomi 17T\nПлюсы и минусы"

        assertThat(plainRichText(text).text).isEqualTo(text)
    }

    @Test
    fun linksTimestampsAndHashtagsBecomeSpans() {
        val text = "Розыгрыши: https://t.me/modnox/4579\n0:00 Вступление\n1:02:03 Итоги #xiaomi"

        val spans = plainRichText(text).spans

        assertThat(spans.map { it.target })
            .containsExactly(
                RichTextTarget.Url("https://t.me/modnox/4579"),
                RichTextTarget.Timestamp(0),
                RichTextTarget.Timestamp(3_723),
                RichTextTarget.Hashtag("xiaomi"),
            ).inOrder()
        assertThat(text.substring(spans[0].start, spans[0].end)).isEqualTo("https://t.me/modnox/4579")
    }

    @Test
    fun aTimeInsideALinkStaysPartOfTheLink() {
        val spans = plainRichText("https://example.com/at/10:20 more").spans

        assertThat(spans.map { it.target }).containsExactly(RichTextTarget.Url("https://example.com/at/10:20"))
    }

    @Test
    fun anAnchorInsideALinkIsNotAHashtag() {
        val spans = plainRichText("https://example.com/page#top").spans

        assertThat(spans).hasSize(1)
    }
}
