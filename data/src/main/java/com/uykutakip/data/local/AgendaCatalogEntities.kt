package com.uykutakip.data.local

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.ColumnInfo

@Entity(tableName = "agenda_task_templates")
internal data class AgendaTaskTemplateEntity(
    @PrimaryKey val id: String,
    val text: String,
    val category: String = "",
    val subcategory: String = "",
    val favorite: Boolean = false,
    val builtIn: Boolean = false,
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "agenda_categories",
    indices = [Index(value = ["category", "subcategory"], unique = true)],
)
internal data class AgendaCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val subcategory: String = "",
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "agenda_hidden_task_suggestions")
internal data class AgendaHiddenTaskSuggestionEntity(
    @PrimaryKey val suggestionKey: String,
    val hiddenAtMillis: Long = System.currentTimeMillis(),
)
