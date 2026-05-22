package com.example.trnberechnung.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.trnberechnung.repository.TideRepository

class TideViewModelFactory(
    private val repository: TideRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(TideViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")

            return TideViewModel(repository) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}