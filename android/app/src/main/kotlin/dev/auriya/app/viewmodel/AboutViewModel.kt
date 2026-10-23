package dev.auriya.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.auriya.app.data.repository.AboutRepository
import dev.auriya.app.data.repository.Contributor
import dev.auriya.app.data.repository.OwnerInfo
import dev.auriya.app.data.repository.RepoInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AboutViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = AboutRepository(app.applicationContext)

    private val _repoInfo = MutableStateFlow(RepoInfo("Auriya", "Technical documentation for Auriya and personal optimization experiments.", "2.0.0"))
    val repoInfo: StateFlow<RepoInfo> = _repoInfo.asStateFlow()

    private val _ownerInfo = MutableStateFlow(repo.getCachedOwner())
    val ownerInfo: StateFlow<OwnerInfo> = _ownerInfo.asStateFlow()

    private val _contributors = MutableStateFlow(repo.getCachedContributors())
    val contributors: StateFlow<List<Contributor>> = _contributors.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadData()
    }

    fun loadData(forceRefresh: Boolean = false) {
        if (!forceRefresh && !repo.shouldRefresh() && _contributors.value.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val info = repo.fetchRepoInfo()
                _repoInfo.value = info

                val owner = repo.fetchOwnerInfo()
                repo.saveOwner(owner)
                _ownerInfo.value = owner

                val list = repo.fetchContributors()
                if (list.isNotEmpty()) {
                    repo.saveContributors(list)
                    _contributors.value = list
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
