package com.fluilet.app.data.mapper

import com.fluilet.app.data.remote.dto.ChildCreateDto
import com.fluilet.app.data.remote.dto.ChildDto
import com.fluilet.app.data.remote.dto.ChildUpdateDto
import com.fluilet.app.data.remote.dto.UserDto
import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.ChildDraft
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.domain.model.DifficultyType
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.model.User
import com.fluilet.app.domain.model.UserRole
import java.time.LocalDate
import kotlin.math.roundToInt

/** `null` si el rol es desconocido: el llamador decide el fallback seguro (nunca un crash). */
fun UserDto.toDomain(): User? {
    val parsed = UserRole.fromWire(role) ?: return null
    return User(id, email, fullName, parsed, isActive, isVerified)
}

fun ChildDto.toDomain(): ChildProfile = ChildProfile(
    id = id,
    name = name,
    birthDate = runCatching { LocalDate.parse(birthDate) }.getOrDefault(LocalDate.now()),
    difficulty = DifficultyType.fromWire(difficulty),
    nlpLevel = NlpLevel.fromValue(nlpLevel),
    palette = AccessiblePalette.fromBackend(palette),
    fontSizeSp = (fontSize ?: ReadingSettings.DEFAULT_FONT_SIZE.toInt()).toFloat(),
    lineSpacing = (lineSpacing ?: ReadingSettings.DEFAULT_LINE_SPACING.toDouble()).toFloat(),
    isActive = isActive
)

fun ChildDraft.toCreateDto(): ChildCreateDto = ChildCreateDto(
    name = name.trim(),
    birthDate = birthDate.toString(),
    difficulty = difficulty.wireValue,
    nlpLevel = nlpLevel.value,
    palette = palette.backendValue,
    fontSize = fontSizeSp.roundToInt().coerceIn(14, 28),
    lineSpacing = lineSpacing.toDouble().coerceIn(1.5, 2.5)
)

/** El backend no permite cambiar `birth_date` en PATCH. */
fun ChildDraft.toUpdateDto(): ChildUpdateDto = ChildUpdateDto(
    name = name.trim(),
    difficulty = difficulty.wireValue,
    nlpLevel = nlpLevel.value,
    palette = palette.backendValue,
    fontSize = fontSizeSp.roundToInt().coerceIn(14, 28),
    lineSpacing = lineSpacing.toDouble().coerceIn(1.5, 2.5)
)
