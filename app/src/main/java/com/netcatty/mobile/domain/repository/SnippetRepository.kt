package com.netcatty.mobile.domain.repository

import com.netcatty.mobile.domain.model.Snippet
import kotlinx.coroutines.flow.Flow

interface SnippetRepository {
    fun getAllSnippets(): Flow<List<Snippet>>
    suspend fun getSnippetById(id: String): Snippet?
    suspend fun insertSnippet(snippet: Snippet)
    suspend fun deleteSnippet(snippet: Snippet)
}
