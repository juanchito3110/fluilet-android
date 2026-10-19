package com.fluilet.app.data.repository

import com.fluilet.app.core.network.apiCall
import com.fluilet.app.data.mapper.toCreateDto
import com.fluilet.app.data.mapper.toDomain
import com.fluilet.app.data.mapper.toUpdateDto
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.data.remote.dto.ChildUpdateDto
import com.fluilet.app.domain.model.ChildDraft
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.repository.ChildRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChildRepositoryImpl @Inject constructor(
    private val api: FluiletApi
) : ChildRepository {

    override suspend fun list(): Result<List<ChildProfile>> =
        apiCall { api.listChildren() }.map { list -> list.map { it.toDomain() }.filter { it.isActive } }

    override suspend fun create(draft: ChildDraft): Result<ChildProfile> =
        apiCall { api.createChild(draft.toCreateDto()) }.map { it.toDomain() }

    override suspend fun update(id: String, draft: ChildDraft): Result<ChildProfile> =
        apiCall { api.updateChild(id, draft.toUpdateDto()) }.map { it.toDomain() }

    override suspend fun updateNlpLevel(id: String, level: NlpLevel): Result<ChildProfile> =
        apiCall { api.updateChild(id, ChildUpdateDto(nlpLevel = level.value)) }.map { it.toDomain() }

    override suspend fun delete(id: String): Result<Unit> = apiCall { api.deleteChild(id) }
}
