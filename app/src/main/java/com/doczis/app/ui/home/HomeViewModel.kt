package com.doczis.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doczis.app.DoczisApp
import com.doczis.app.data.db.FileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as DoczisApp).fileRepository
    val allFiles: Flow<List<FileEntity>> = repository.allFiles

    private val _searchQuery = MutableStateFlow("")

    private val _isEmpty = MutableStateFlow(true)
    val isEmpty: StateFlow<Boolean> = _isEmpty.asStateFlow()

    val displayFiles: Flow<List<FileEntity>> = combine(allFiles, _searchQuery) { files, query ->
        if (query.isBlank()) files
        else files.filter { it.fileName.contains(query, ignoreCase = true) }
    }

    init {
        viewModelScope.launch {
            repository.allFiles.collect { files ->
                _isEmpty.value = files.isEmpty()
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun renameFile(file: FileEntity, newName: String, newPath: String) {
        viewModelScope.launch { repository.update(file.id, newName, newPath) }
    }

    fun deleteFile(file: FileEntity) {
        viewModelScope.launch { repository.delete(file) }
    }

    fun saveRecentFile(fileName: String, filePath: String, fileSize: Long) {
        viewModelScope.launch {
            val existing = repository.findByFileName(fileName)
            if (existing != null) {
                repository.update(existing.id, fileName, filePath)
                return@launch
            }
            repository.insert(
                com.doczis.app.data.db.FileEntity(
                    fileName = fileName,
                    filePath = filePath,
                    fileSize = fileSize,
                    toolType = "viewed"
                )
            )
        }
    }

    fun clearAll() {
        viewModelScope.launch { repository.deleteAll() }
    }
}
