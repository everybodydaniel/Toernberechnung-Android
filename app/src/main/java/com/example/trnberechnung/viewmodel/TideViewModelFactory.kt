package com.example.trnberechnung.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.trnberechnung.repository.TideRepository

// Factory erstellt ein ViewModel mit einem Parameter
class TideViewModelFactory(
    private val repository: TideRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Prüft, ob das gewünschte ViewModel TideViewModel ist
        if (modelClass.isAssignableFrom(TideViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Erstellt das ViewModel mit Repository
            return TideViewModel(repository) as T
        }

        // Fehler, falls eine unbekannte Klasse angefordert wird
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}