package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interactions")
data class Interaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val userInput: String,
    val assistantReply: String,
    val action: String = "NONE",
    val argument: String = "",
    val isSystemLog: Boolean = false
)
