package com.doczis.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM recent_files ORDER BY createdAt DESC")
    fun getAllFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM recent_files WHERE fileName = :fileName LIMIT 1")
    suspend fun findByFileName(fileName: String): FileEntity?

    @Insert
    suspend fun insert(file: FileEntity): Long

    @Query("SELECT * FROM recent_files WHERE filePath = :filePath LIMIT 1")
    suspend fun findByFilePath(filePath: String): FileEntity?

    @Query("UPDATE recent_files SET createdAt = :timestamp WHERE id = :id")
    suspend fun touch(id: Long, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(file: FileEntity)

    @Query("UPDATE recent_files SET fileName = :newName, filePath = :newPath WHERE id = :id")
    suspend fun update(id: Long, newName: String, newPath: String)

    @Query("DELETE FROM recent_files")
    suspend fun deleteAll()
}
