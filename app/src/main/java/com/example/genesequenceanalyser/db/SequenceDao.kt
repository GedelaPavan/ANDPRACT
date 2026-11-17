package com.example.genesequenceanalyser.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SequenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sequence: SequenceEntity)

    @Query("SELECT * FROM sequence_history ORDER BY timestamp DESC")
    fun getAllHistory(): LiveData<List<SequenceEntity>>

    @Query("SELECT * FROM sequence_history WHERE accessionId LIKE :query ORDER BY timestamp DESC")
    fun searchHistory(query: String): LiveData<List<SequenceEntity>>

    @Delete
    suspend fun delete(sequence: SequenceEntity)
}