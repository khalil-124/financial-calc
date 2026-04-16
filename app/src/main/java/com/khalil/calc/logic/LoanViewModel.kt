package com.khalil.calc.logic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LoanViewModel(application: Application) : AndroidViewModel(application) {
    
    private val loanDao = AppDatabase.getDatabase(application).loanDao()

    val savedLoans: StateFlow<List<SavedLoan>> = loanDao.getAllLoans()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveLoan(loan: SavedLoan) {
        viewModelScope.launch {
            loanDao.insertLoan(loan)
        }
    }

    fun deleteLoan(loan: SavedLoan) {
        viewModelScope.launch {
            loanDao.deleteLoan(loan)
        }
    }
}
