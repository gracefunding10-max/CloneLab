package com.example.clonelab.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClonedAppDao {

    @Query("SELECT * FROM cloned_apps ORDER BY id ASC")
    fun getAllClones(): Flow<List<ClonedApp>>

    @Query("SELECT * FROM cloned_apps WHERE id = :id LIMIT 1")
    fun getCloneById(id: Long): Flow<ClonedApp?>

    @Query("SELECT * FROM cloned_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getCloneByPackage(packageName: String): ClonedApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clone: ClonedApp): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clones: List<ClonedApp>)

    @Update
    suspend fun update(clone: ClonedApp)

    @Delete
    suspend fun delete(clone: ClonedApp)

    @Query("DELETE FROM cloned_apps WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM cloned_apps")
    suspend fun count(): Int

    @Query("UPDATE cloned_apps SET cameraRedirectEnabled = :enabled WHERE id = :id")
    suspend fun setRedirectEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE cloned_apps SET cameraSourceUri = :uri, sourceType = :sourceType WHERE id = :id")
    suspend fun updateCameraSource(id: Long, uri: String?, sourceType: CameraSourceType)
}
