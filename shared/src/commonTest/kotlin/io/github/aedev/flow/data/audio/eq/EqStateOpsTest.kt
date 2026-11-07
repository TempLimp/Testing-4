package io.github.aedev.flow.data.audio.eq

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class EqStateOpsTest {
    private val rock = "builtin:rock"
    private val mine = EqPreset("user:a", "Mine", EqCurve(bands = listOf(EqBand(500.0, 3.0))))

    @Test
    fun selectingAPresetLoadsItsCurveAndIsNotAnEdit() {
        val state = EqState().selectPreset(rock)
        assertThat(state.active.presetId).isEqualTo(rock)
        assertThat(state.active.curve).isEqualTo(BuiltInEqPresets.byId(rock)!!.curve)
        assertThat(state.isEdited).isFalse()
    }

    @Test
    fun anEditedCurveStaysReachableAndRevertsToItsPreset() {
        val edited = EqState().selectPreset(rock).withBand(0, EqBand(60.0, 1.0, 0.707, EqFilterType.LOW_SHELF))
        assertThat(edited.isEdited).isTrue()
        assertThat(edited.active.presetId).isEqualTo(rock)
        assertThat(edited.revert().isEdited).isFalse()
    }

    @Test
    fun aBuiltInCannotBeOverwrittenAUserPresetCan() {
        val builtIn = EqState().selectPreset(rock).addBand(EqBand(8_000.0, 2.0))
        assertThat(builtIn.canSaveActive).isFalse()
        assertThat(builtIn.saveActive()).isEqualTo(builtIn)

        val own = EqState(userPresets = listOf(mine)).selectPreset(mine.id).addBand(EqBand(8_000.0, 2.0))
        val saved = own.saveActive()
        assertThat(saved.userPreset(mine.id)!!.curve.bands).hasSize(2)
        assertThat(saved.isEdited).isFalse()
    }

    @Test
    fun saveAsCreatesAPresetAndSelectsIt() {
        val state = EqState().selectPreset(rock).addBand(EqBand(8_000.0, 2.0)).saveActiveAs("user:b", "Car")
        assertThat(state.active.presetId).isEqualTo("user:b")
        assertThat(state.userPreset("user:b")!!.name).isEqualTo("Car")
        assertThat(state.isEdited).isFalse()
    }

    @Test
    fun deletingASelectedPresetKeepsTheSoundAndUndoRestoresItInPlace() {
        val other = mine.copy(id = "user:z", name = "Other")
        val state = EqState(userPresets = listOf(other, mine)).selectPreset(mine.id)
        val (deleted, removed) = state.deletePreset(mine.id)

        assertThat(deleted.active.presetId).isNull()
        assertThat(deleted.active.curve).isEqualTo(mine.curve)

        val restored = deleted.restorePreset(removed!!)
        assertThat(restored.userPresets.map { it.id }).containsExactly("user:z", mine.id).inOrder()
        assertThat(restored.active.presetId).isEqualTo(mine.id)
    }

    @Test
    fun eachModeKeepsItsOwnCurve() {
        val parametric = EqState().selectPreset(rock)
        val graphic = parametric.copy(mode = EqMode.GRAPHIC).withActiveCurve(GraphicEq.curveOf(List(10) { 3.0 }))
        val back = graphic.copy(mode = EqMode.PARAMETRIC)
        assertThat(back.active.curve).isEqualTo(parametric.active.curve)
        assertThat(
            back.graphic.curve.bands
                .map { it.gain },
        ).containsExactlyElementsIn(List(10) { 3.0 })
    }

    @Test
    fun importingAddsAPresetAndPlaysIt() {
        val curve = EqCurve(-2.0, listOf(EqBand(910.0, 9.0, 0.71)))
        val state = EqState().importPreset("user:i", "Hefty Metal", curve)
        assertThat(state.active.presetId).isEqualTo("user:i")
        assertThat(state.userPreset("user:i")!!.imported).isTrue()
        assertThat(state.mode).isEqualTo(EqMode.PARAMETRIC)
    }

    @Test
    fun theProcessingSpecAddsBassBoostAndProtectsThePeak() {
        val state = EqState().selectPreset(rock).withBassBoost(6.0)
        val spec = state.processingSpec(bypass = false)
        assertThat(spec.enabled).isTrue()
        assertThat(spec.bands.last().type).isEqualTo(EqFilterType.LOW_SHELF)
        assertThat(spec.bands.last().gain).isEqualTo(6.0)
        assertThat(spec.preampDb).isWithin(0.01).of(EqFilterMath.autoPreampDb(spec.bands))
        assertThat(spec.preampDb).isLessThan(0.0)
    }

    @Test
    fun manualPreampIsUsedWhenAutoPreampIsOff() {
        val state = EqState(autoPreamp = false).selectPreset(rock).withManualPreamp(-3.0)
        assertThat(state.processingSpec(bypass = false).preampDb).isEqualTo(-3.0)
    }

    @Test
    fun offAndCompareBothProduceAnUnprocessedSpec() {
        val state = EqState().selectPreset(rock)
        assertThat(state.copy(enabled = false).processingSpec(bypass = false)).isEqualTo(EqProcessingSpec.OFF)
        assertThat(state.processingSpec(bypass = true)).isEqualTo(EqProcessingSpec.OFF)
    }

    @Test
    fun aPreviewReplacesThePlayingCurveWithoutTouchingTheState() {
        val preview = EqCurve(bands = listOf(EqBand(2_000.0, 5.0)))
        val spec = EqState().selectPreset(rock).processingSpec(bypass = false, preview = preview)
        assertThat(spec.bands).containsExactly(EqBand(2_000.0, 5.0))
    }

    @Test
    fun flatAndOffSettingsChangeNothingSoOffloadCanStayOn() {
        assertThat(EqState().changesSound).isFalse()
        assertThat(EqState().selectPreset(rock).changesSound).isTrue()
        assertThat(EqState().selectPreset(rock).copy(enabled = false).changesSound).isFalse()
        assertThat(EqState().withBassBoost(3.0).changesSound).isTrue()
        assertThat(EqState().addBand(EqBand(1_000.0, 0.0)).changesSound).isFalse()
    }

    @Test
    fun namesAreUniqueRegardlessOfCaseIncludingBuiltInNames() {
        val state = EqState(userPresets = listOf(mine))
        assertThat(state.isNameAvailable("mine", listOf("Rock"))).isFalse()
        assertThat(state.isNameAvailable("ROCK", listOf("Rock"))).isFalse()
        assertThat(state.isNameAvailable("Mine", listOf("Rock"), exceptId = mine.id)).isTrue()
        assertThat(state.isNameAvailable("  ", listOf("Rock"))).isFalse()
    }

    @Test
    fun bandEditsRespectTheBandLimitAndGraphicMode() {
        val full = (1..EqLimits.MAX_BANDS).fold(EqState()) { state, i -> state.addBand(EqBand(i * 100.0, 1.0)) }
        assertThat(full.addBand(EqBand(5_000.0, 1.0))).isEqualTo(full)
        val graphic = EqState(mode = EqMode.GRAPHIC)
        assertThat(graphic.addBand(EqBand(5_000.0, 1.0))).isEqualTo(graphic)
    }

    @Test
    fun sanitisingRepairsADamagedState() {
        val damaged =
            EqState(
                graphic = EqWorkingCopy(null, EqCurve(bands = listOf(EqBand(1.0, 99.0)))),
                bassBoost = Double.NaN,
                userPresets = listOf(mine, mine, EqPreset("", "x", EqCurve())),
            )
        val clean = damaged.sanitized()
        assertThat(clean.graphic.curve).isEqualTo(GraphicEq.flatCurve())
        assertThat(clean.bassBoost).isEqualTo(0.0)
        assertThat(clean.userPresets).containsExactly(mine)
    }

    @Test
    fun stateSurvivesItsJSONForm() {
        val state = EqState(userPresets = listOf(mine)).selectPreset(mine.id).withBassBoost(4.0)
        assertThat(EqStateJson.decode(EqStateJson.encode(state))).isEqualTo(state)
        assertThat(EqStateJson.decode("not json")).isNull()
    }

    @Test
    fun aPresetPickedInGraphicModeIsFittedOntoTheTenBandsAndStaysGraphic() {
        val state = EqState(mode = EqMode.GRAPHIC).selectPreset(rock)
        val rockBands = BuiltInEqPresets.byId(rock)!!.curve.bands

        assertThat(state.mode).isEqualTo(EqMode.GRAPHIC)
        assertThat(state.active.presetId).isEqualTo(rock)
        assertThat(state.isEdited).isFalse()
        assertThat(state.parametric).isEqualTo(EqState().parametric)
        val grid = (0..80).map { EqFilterMath.frequencyAt(0.05 + 0.9 * it / 80.0) }
        val error = grid.map { EqFilterMath.magnitudeDb(state.graphic.curve.bands, it) - EqFilterMath.magnitudeDb(rockBands, it) }
        val rms = kotlin.math.sqrt(error.sumOf { it * it } / error.size)
        assertThat(rms).isLessThan(1.0)
        val gains = GraphicEq.gainsOf(state.graphic.curve)
        val zigzags =
            gains.windowed(3).count { (a, b, c) ->
                (b - a) * (c - b) < 0 && kotlin.math.abs(b - a) > 1.5 &&
                    kotlin.math.abs(c - b) > 1.5
            }
        assertThat(zigzags).isEqualTo(0)
    }

    @Test
    fun revertInGraphicModeReturnsToTheFittedPreset() {
        val picked = EqState(mode = EqMode.GRAPHIC).selectPreset(rock)
        val moved = picked.withActiveCurve(GraphicEq.curveOf(List(10) { 6.0 }))
        assertThat(moved.isEdited).isTrue()
        assertThat(moved.revert()).isEqualTo(picked)
    }

    @Test
    fun aGraphicPresetPickedInParametricModeLoadsItsBandsAndStaysParametric() {
        val car = EqPreset("user:car", "Car", GraphicEq.curveOf(List(10) { it.toDouble() }), mode = EqMode.GRAPHIC)
        val state = EqState(userPresets = listOf(car)).selectPreset(car.id)

        assertThat(state.mode).isEqualTo(EqMode.PARAMETRIC)
        assertThat(state.active.curve.bands).hasSize(10)
        assertThat(state.isEdited).isFalse()
    }

    @Test
    fun importingFromGraphicModeSwitchesToParametricWhereTheFilePlaysExactly() {
        val curve = EqCurve(bands = listOf(EqBand(910.0, 9.0, 0.71)))
        val state = EqState(mode = EqMode.GRAPHIC).importPreset("user:i", "Hefty Metal", curve)
        assertThat(state.mode).isEqualTo(EqMode.PARAMETRIC)
        assertThat(state.active.curve).isEqualTo(curve)
    }

    @Test
    fun fittingFlatGivesFlatAndNeverWritesANegativeZero() {
        assertThat(GraphicEq.fit(EqCurve())).isEqualTo(GraphicEq.flatCurve())
    }
}
