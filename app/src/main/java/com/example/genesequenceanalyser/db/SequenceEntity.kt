package com.example.genesequenceanalyser.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sequence_history")
data class SequenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val accessionId: String,
    val description: String,
    val sequenceLength: Int,
    val gcPercentage: Double,
    val meltingTemp: Double,
    val timestamp: Long = System.currentTimeMillis()
)