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

    @Query("SELECT * FROM active_loans ORDER BY createdAt DESC")
    fun getAllActiveLoans(): Flow<List<ActiveLoan>>

    @Query("SELECT * FROM active_loans ORDER BY createdAt DESC")
    suspend fun getAllActiveLoansSync(): List<ActiveLoan>

    @Insert
    suspend fun insertActiveLoan(loan: ActiveLoan)

    @Delete
    suspend fun deleteActiveLoan(loan: ActiveLoan)
}
