package com.example.trnberechnung.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trnberechnung.repository.NorthSeaWarningRepository
import com.example.trnberechnung.repository.WarningSourceSyncState
import com.example.trnberechnung.warnings.NorthSeaWarning
import com.example.trnberechnung.warnings.OfficialWarningDocument
import com.example.trnberechnung.warnings.WarningCategory
import com.example.trnberechnung.warnings.WarningSourceId
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NorthSeaWarningsUiState(
    val warnings: List<NorthSeaWarning> = emptyList(),
    val documents: List<OfficialWarningDocument> = emptyList(),
    val sourceStates: Map<WarningSourceId, WarningSourceSyncState> = emptyMap(),
    val selectedCategory: WarningCategory? = null,
    val expandedIds: Set<String> = emptySet(),
    val loadingDetailIds: Set<String> = emptySet(),
    val detailErrors: Map<String, String> = emptyMap(),
    val isRefreshing: Boolean = false,
    val revealedWarningId: String? = null,
) {
    val filteredWarnings: List<NorthSeaWarning>
        get() =
            selectedCategory?.let { selected -> warnings.filter { it.category == selected } }
                ?: warnings

    val elwisState: WarningSourceSyncState?
        get() = sourceStates[WarningSourceId.ELWIS]

    val isInitialLoading: Boolean
        get() = elwisState == null && warnings.isEmpty()

    val isStale: Boolean
        get() = elwisState?.isStale == true && warnings.isNotEmpty()

    val isIncomplete: Boolean
        get() =
            elwisState?.isIncomplete == true ||
                warnings.any { warning -> !warning.isComplete }

    val sourceError: String?
        get() = elwisState?.lastError

    val lastSuccessfulUpdate: Instant?
        get() = elwisState?.lastSuccessAt
}

class NorthSeaWarningsViewModel(
    private val repository: NorthSeaWarningRepository,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            NorthSeaWarningsUiState(documents = repository.documents),
        )
    val uiState: StateFlow<NorthSeaWarningsUiState> = _uiState.asStateFlow()

    private var screenVisible = false

    init {
        viewModelScope.launch {
            repository.state.collect { repositoryState ->
                _uiState.update { current ->
                    current.copy(
                        warnings = repositoryState.warnings,
                        sourceStates = repositoryState.sourceStates,
                    )
                }
                if (screenVisible && repositoryState.warnings.isNotEmpty()) {
                    repository.markVisibleWarningsSeen()
                }
            }
        }
    }

    fun selectCategory(category: WarningCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun toggleExpanded(id: String) {
        val expanding = id !in _uiState.value.expandedIds
        _uiState.update { current ->
            current.copy(
                expandedIds =
                    if (expanding) {
                        current.expandedIds + id
                    } else {
                        current.expandedIds - id
                    },
            )
        }
        val warning = _uiState.value.warnings.firstOrNull { it.id == id }
        if (expanding && warning != null) {
            loadDetails(id)
        }
    }

    fun showWarning(id: String) {
        _uiState.update { current ->
            current.copy(
                selectedCategory = null,
                expandedIds = current.expandedIds + id,
                revealedWarningId = id,
            )
        }
        val warning = _uiState.value.warnings.firstOrNull { it.id == id }
        if (warning != null) {
            loadDetails(id)
        }
    }

    fun clearRevealRequest() {
        _uiState.update { it.copy(revealedWarningId = null) }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                repository.refresh()
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun onScreenOpened() {
        screenVisible = true
        viewModelScope.launch { repository.markVisibleWarningsSeen() }
    }

    fun onScreenClosed() {
        screenVisible = false
    }

    private fun loadDetails(id: String) {
        if (id in _uiState.value.loadingDetailIds) return
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    loadingDetailIds = current.loadingDetailIds + id,
                    detailErrors = current.detailErrors - id,
                )
            }
            val result = repository.loadDetails(id)
            if (result.isSuccess && screenVisible) {
                repository.markVisibleWarningsSeen()
            }
            result.onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            detailErrors =
                                current.detailErrors +
                                    (id to
                                        (error.message
                                            ?: "Die amtlichen Details konnten nicht geladen werden.")),
                        )
                    }
                }
            _uiState.update { current ->
                current.copy(loadingDetailIds = current.loadingDetailIds - id)
            }
        }
    }

    class Factory(
        private val repository: NorthSeaWarningRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NorthSeaWarningsViewModel(repository) as T
    }
}
