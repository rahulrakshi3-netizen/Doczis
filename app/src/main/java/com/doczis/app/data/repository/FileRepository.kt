package com.doczis.app.data.repository

import com.doczis.app.data.db.FileDao
import com.doczis.app.data.db.FileEntity
import kotlinx.coroutines.flow.Flow

class FileRepository(private val fileDao: FileDao) {
    val allFiles: Flow<List<FileEntity>> = fileDao.getAllFiles()

    suspend fun insert(file: FileEntity) = fileDao.insert(file)

    suspend fun findByFilePath(filePath: String) = fileDao.findByFilePath(filePath)

    suspend fun findByFileName(fileName: String) = fileDao.findByFileName(fileName)

    suspend fun touch(id: Long, timestamp: Long = System.currentTimeMillis()) = fileDao.touch(id, timestamp)

    suspend fun delete(file: FileEntity) = fileDao.delete(file)

    suspend fun update(id: Long, newName: String, newPath: String) = fileDao.update(id, newName, newPath)

    suspend fun deleteAll() = fileDao.deleteAll()
}
