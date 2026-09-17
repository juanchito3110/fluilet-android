package com.fluilet.app.domain.usecase

import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.ReadingSettings
import javax.inject.Inject

/** Cada UseCase valida y clampa el input antes de tocar el dominio: nunca confiamos en el valor crudo de la UI. */
class UpdateLineSpacingUseCase @Inject constructor() {
    operator fun invoke(current: ReadingSettings, newValue: Float): Result<ReadingSettings> = runCatching {
        val clamped = newValue.coerceIn(ReadingSettings.MIN_LINE_SPACING, ReadingSettings.MAX_LINE_SPACING)
        current.copy(lineSpacing = clamped)
    }
}

class UpdateFontSizeUseCase @Inject constructor() {
    operator fun invoke(current: ReadingSettings, newValue: Float): Result<ReadingSettings> = runCatching {
        val clamped = newValue.coerceIn(ReadingSettings.MIN_FONT_SIZE, ReadingSettings.MAX_FONT_SIZE)
        current.copy(fontSizeSp = clamped)
    }
}

class UpdatePaletteUseCase @Inject constructor() {
    operator fun invoke(current: ReadingSettings, palette: AccessiblePalette): Result<ReadingSettings> = runCatching {
        current.copy(palette = palette)
    }
}