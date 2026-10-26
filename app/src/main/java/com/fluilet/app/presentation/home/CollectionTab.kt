package com.fluilet.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.presentation.fluibot.AlbumSection
import com.fluilet.app.presentation.fluibot.FluiBotUiState
import com.fluilet.app.presentation.fluibot.MissionsCard

/** 🃏 Colección y Misiones: 🎯 misiones diarias, 🃏 álbum de cartas y 🏅 insignias. */
@Composable
internal fun CollectionTab(bot: FluiBotUiState, progress: EducationalProgress, onClaim: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MissionsCard(bot.missions, onClaim)
        AlbumSection(bot.cards.ifEmpty { progress.cards }, bot.badges.ifEmpty { progress.badges })
    }
}
