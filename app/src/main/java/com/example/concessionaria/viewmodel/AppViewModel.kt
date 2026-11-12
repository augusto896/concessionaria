package com.example.concessionaria.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.concessionaria.data.repository.ConcessionariaRepository
import com.example.concessionaria.domain.Dashboard
import com.example.concessionaria.domain.Record
import com.example.concessionaria.domain.Session
import com.example.concessionaria.preferences.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val checkingSession: Boolean = true,
    val isAuthenticated: Boolean = false,
    val user: Record? = null,
    val dashboard: Dashboard? = null,
    val records: List<Record> = emptyList(),
    val lookups: Map<String, List<Record>> = emptyMap(),
    val loading: Boolean = false,
    val message: String? = null
)

class AppViewModel(
    private val repository: ConcessionariaRepository,
    private val preferences: UserPreferences
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = preferences.settings.first()
            mutableState.update {
                it.copy(
                    checkingSession = false,
                    isAuthenticated = settings.token.isNotBlank(),
                    user = if (settings.token.isNotBlank()) {
                        Record(nome = settings.userName, cargo = settings.userRole)
                    } else null
                )
            }
            if (settings.token.isNotBlank()) refreshDashboard()
        }
    }

    fun login(identity: String, password: String) {
        if (identity.isBlank() || password.isBlank()) {
            showMessage("Informe usuário/email e senha.")
            return
        }
        runRequest {
            val session = repository.login(identity.trim(), password)
            preferences.saveSession(
                session.token,
                session.usuario.nome.orEmpty(),
                session.usuario.cargo.orEmpty()
            )
            mutableState.update {
                it.copy(isAuthenticated = true, user = session.usuario, message = "Acesso autorizado.")
            }
            refreshDashboard()
        }
    }

    fun logout() {
        runRequest {
            repository.logout()
            preferences.clearSession()
            mutableState.value = AppUiState(checkingSession = false, message = "Sessão encerrada.")
        }
    }

    fun refreshDashboard() = runRequest {
        mutableState.update { it.copy(dashboard = repository.dashboard()) }
    }

    fun loadResource(resource: String) = runRequest {
        mutableState.update { it.copy(records = repository.list(resource)) }
    }

    fun loadLookups(resources: List<String>) = runRequest {
        val values = mutableState.value.lookups.toMutableMap()
        resources.distinct().forEach { resource -> values[resource] = repository.list(resource) }
        mutableState.update { it.copy(lookups = values) }
    }

    fun save(resource: String, id: Long?, record: Record) = runRequest {
        repository.save(resource, id, record)
        mutableState.update { it.copy(message = if (id == null) "Cadastro realizado." else "Alterações salvas.") }
        mutableState.update { it.copy(records = repository.list(resource)) }
        if (resource == "veiculos" || resource == "vendas" || resource == "clientes") refreshDashboard()
    }

    fun delete(resource: String, id: Long) = runRequest {
        repository.delete(resource, id)
        mutableState.update { it.copy(message = "Registro excluído.") }
        mutableState.update { it.copy(records = repository.list(resource)) }
        if (resource == "veiculos" || resource == "clientes") refreshDashboard()
    }

    fun showMessage(message: String) {
        mutableState.update { it.copy(message = message) }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private fun runRequest(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, message = null) }
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(message = error.message ?: "Não foi possível concluir a operação.")
                }
            } finally {
                mutableState.update { it.copy(loading = false) }
            }
        }
    }

    companion object {
        fun factory(
            repository: ConcessionariaRepository,
            preferences: UserPreferences
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
                    return AppViewModel(repository, preferences) as T
                }
                error("ViewModel não suportada: ${modelClass.name}")
            }
        }
    }
}
