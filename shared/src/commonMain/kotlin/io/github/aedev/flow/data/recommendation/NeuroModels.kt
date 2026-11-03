/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 *
 * Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This recommendation algorithm (FlowNeuroEngine) is the intellectual property
 * of the Flow project. Any use of this code in other projects must
 * explicitly credit "Flow Android Client" and link back to the original repository.
 */
package io.github.aedev.flow.data.recommendation

// S07 pulled these three forward for data/shorts; S09 ports the rest of upstream's NeuroModels.kt around them.

// ── Interaction Types ──

enum class InteractionType {
    CLICK,
    LIKED,
    WATCHED,
    SKIPPED,
    DISLIKED,

    /** High-intent save: download, Watch Later, playlist add. Positive but weaker than LIKED. */
    SAVED,
}

/** Where a reel seed comes from; the weights live in [ShortsSeedSelector]. */
enum class ShortsSeedSource {
    LIKED,
    WANT_MORE,
    SAVED,
    WATCHED,

    /** A reel the user dwelt on this session — usable, weakest. */
    FEED,
}

/** A reel the user engaged with, offered as the seed of a related reel chain. */
data class ShortsSeedInput(
    val id: String,
    val channelId: String,
    val source: ShortsSeedSource,
    val timestamp: Long,
    /** Watched share for [ShortsSeedSource.WATCHED]; ignored elsewhere. */
    val percentWatched: Double = 100.0,
)
