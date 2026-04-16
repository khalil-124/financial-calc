package com.khalil.calc.logic

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Query("SELECT * FROM saved_loans ORDER BY createdAt DESC")
    fun getAllLoans(): Flow<List<SavedLoan>>

    @Insert
    suspend fun insertLoan(loan: SavedLoan)

    @Delete
    suspend fun deleteLoan(loan: SavedLoan)
}
