package com.netcatty.mobile.data.repository

import com.netcatty.mobile.data.local.dao.SnippetDao
import com.netcatty.mobile.data.mapper.toDomain
import com.netcatty.mobile.data.mapper.toEntity
import com.netcatty.mobile.domain.model.Snippet
import com.netcatty.mobile.domain.repository.SnippetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnippetRepositoryImpl @Inject constructor(
    private val snippetDao: SnippetDao
) : SnippetRepository {

    override fun getAllSnippets(): Flow<List<Snippet>> {
        return snippetDao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getSnippetById(id: String): Snippet? {
        return snippetDao.getById(id)?.toDomain()
    }

    override suspend fun insertSnippet(snippet: Snippet) {
        snippetDao.upsert(snippet.toEntity())
    }

    override suspend fun deleteSnippet(snippet: Snippet) {
        snippetDao.delete(snippet.toEntity())
    }
}
