package com.example.concessionaria

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.concessionaria.data.repository.ConcessionariaRepository
import com.example.concessionaria.domain.Dashboard
import com.example.concessionaria.domain.Record
import com.example.concessionaria.preferences.AppSettings
import com.example.concessionaria.preferences.UserPreferences
import com.example.concessionaria.ui.theme.ConcessionariaTheme
import com.example.concessionaria.viewmodel.AppViewModel
import com.example.concessionaria.viewmodel.AppUiState
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = UserPreferences(applicationContext)
        val repository = ConcessionariaRepository.create(preferences)
        setContent {
            val viewModel: AppViewModel = viewModel(
                factory = AppViewModel.factory(repository, preferences)
            )
            val settings by preferences.settings.collectAsState(initial = AppSettings())
            ConcessionariaTheme(darkTheme = settings.darkTheme, dynamicColor = false) {
                ConcessionariaApp(viewModel, preferences, settings)
            }
        }
    }
}

private data class ResourceSpec(
    val title: String,
    val fields: List<FormField>,
    val createOnly: Boolean = false
)

private data class FormField(
    val key: String,
    val label: String,
    val required: Boolean = true
)

private data class Choice(val value: String, val label: String)

private val resourceSpecs = mapOf(
    "usuarios" to ResourceSpec(
        "Funcionários",
        listOf(
            FormField("nome", "Nome"),
            FormField("usuario", "Usuário"),
            FormField("email", "Email"),
            FormField("cargo", "Cargo (admin ou vendedor)"),
            FormField("senha", "Senha (mínimo 8 caracteres)", required = false)
        )
    ),
    "clientes" to ResourceSpec(
        "Clientes",
        listOf(
            FormField("nome", "Nome"),
            FormField("cpf", "CPF (11 dígitos)"),
            FormField("telefone", "Telefone"),
            FormField("email", "Email", required = false),
            FormField("endereco", "Endereço")
        )
    ),
    "marcas" to ResourceSpec(
        "Marcas",
        listOf(FormField("nome", "Nome"), FormField("pais_origem", "País de origem"))
    ),
    "veiculos" to ResourceSpec(
        "Veículos",
        listOf(
            FormField("marca_id", "Marca"),
            FormField("modelo", "Modelo"),
            FormField("ano", "Ano"),
            FormField("cor", "Cor"),
            FormField("quilometragem", "Quilometragem"),
            FormField("preco", "Preço (R$)"),
            FormField("descricao", "Descrição", required = false),
            FormField("imagem_url", "URL da imagem", required = false)
        )
    ),
    "vendas" to ResourceSpec(
        "Vendas",
        listOf(
            FormField("cliente_id", "Cliente"),
            FormField("veiculo_id", "Veículo disponível"),
            FormField("data_venda", "Data da venda (AAAA-MM-DD)"),
            FormField("valor_venda", "Valor da venda (R$)"),
            FormField("forma_pagamento", "Forma de pagamento")
        ),
        createOnly = true
    )
)

private val resourceNames = listOf("veiculos", "clientes", "marcas", "vendas", "usuarios")

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ConcessionariaApp(
    viewModel: AppViewModel,
    preferences: UserPreferences,
    settings: AppSettings
) {
    val state by viewModel.state.collectAsState()
    val navController = rememberNavController()
    val navEntry by navController.currentBackStackEntryAsState()
    val route = navEntry?.destination?.route.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.isAuthenticated) {
        if (!state.checkingSession) {
            navController.navigate(if (state.isAuthenticated) "dashboard" else "login") {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    if (state.checkingSession) {
        LoadingScreen()
        return
    }

    val currentResource = navEntry?.arguments?.getString("resource")
    val title = when {
        route == "dashboard" -> "Visão geral"
        route == "settings" -> "Configurações"
        currentResource != null -> resourceSpecs[currentResource]?.title ?: "Concessionária"
        else -> "Concessionária"
    }

    Scaffold(
        topBar = {
            if (route != "login") {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (route != "dashboard") {
                            TextButton(onClick = {
                                navController.navigate("dashboard") {
                                    popUpTo("dashboard") { inclusive = false }
                                    launchSingleTop = true
                                }
                            }) { Text("Início") }
                        }
                    },
                    actions = {
                        if (route != "settings") {
                            TextButton(onClick = { navController.navigate("settings") }) {
                                Text("Ajustes")
                            }
                        }
                        if (state.isAuthenticated) {
                            TextButton(onClick = { viewModel.logout() }) { Text("Sair") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (route.startsWith("module/") && currentResource != null &&
                currentResource != "vendas" || route.startsWith("module/vendas")
            ) {
                if (currentResource != "usuarios" || state.user?.cargo == "admin") {
                    FloatingActionButton(onClick = {
                        scope.launch { navController.currentBackStackEntry?.savedStateHandle?.set("new_record", true) }
                    }) { Text("+") }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (state.isAuthenticated) "dashboard" else "login",
            modifier = Modifier.padding(padding)
        ) {
            composable("login") {
                LoginScreen(state.loading, onLogin = viewModel::login)
            }
            composable("dashboard") {
                DashboardScreen(
                    state = state,
                    settings = settings,
                    onOpen = { navController.navigate("module/$it") },
                    onRefresh = viewModel::refreshDashboard
                )
            }
            composable(
                route = "module/{resource}",
                arguments = listOf(navArgument("resource") { type = NavType.StringType })
            ) { entry ->
                val resource = entry.arguments?.getString("resource").orEmpty()
                val openNew by entry.savedStateHandle.getStateFlow("new_record", false)
                    .collectAsState()
                LaunchedEffect(resource) {
                    viewModel.loadResource(resource)
                    val lookups = when (resource) {
                        "veiculos" -> listOf("marcas")
                        "vendas" -> if (state.user?.cargo == "admin") {
                            listOf("clientes", "veiculos", "usuarios")
                        } else listOf("clientes", "veiculos")
                        else -> emptyList()
                    }
                    if (lookups.isNotEmpty()) viewModel.loadLookups(lookups)
                }
                ModuleScreen(
                    resource = resource,
                    state = state,
                    vehicleSort = settings.vehicleSort,
                    isAdmin = state.user?.cargo == "admin",
                    openNew = openNew,
                    onNewConsumed = { entry.savedStateHandle["new_record"] = false },
                    onRefresh = { viewModel.loadResource(resource) },
                    onSave = { id, record -> viewModel.save(resource, id, record) },
                    onDelete = { id -> viewModel.delete(resource, id) }
                )
            }
            composable("settings") {
                SettingsScreen(
                    settings = settings,
                    onUpdate = { displayName, dark, notifications, sort ->
                        scope.launch {
                            preferences.updateSettings(displayName, dark, notifications, sort)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Carregando concessionária…")
    }
}

@Composable
private fun LoginScreen(loading: Boolean, onLogin: (String, String) -> Unit) {
    var identity by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("CONCESSIONÁRIA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Bem-vindo", style = MaterialTheme.typography.headlineLarge)
        Text("Entre para gerenciar sua loja de veículos.")
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = identity,
            onValueChange = { identity = it },
            label = { Text("Usuário ou email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "Ocultar" else "Mostrar")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onLogin(identity, password) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
            else Text("Entrar")
        }
    }
}

@Composable
private fun DashboardScreen(
    state: AppUiState,
    settings: AppSettings,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }
    val dashboard = state.dashboard
    val greeting = settings.displayName.ifBlank { state.user?.nome.orEmpty() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Olá, $greeting", style = MaterialTheme.typography.headlineSmall)
            Text("Resumo atualizado diretamente pela API.")
        }
        if (state.loading && dashboard == null) {
            item { CircularProgressIndicator() }
        } else {
            item { StatCard("Veículos no estoque", dashboard?.total_veiculos ?: 0) }
            item { StatCard("Disponíveis", dashboard?.veiculos_disponiveis ?: 0) }
            item { StatCard("Vendidos", dashboard?.veiculos_vendidos ?: 0) }
            item { StatCard("Clientes", dashboard?.total_clientes ?: 0) }
            item { StatCard("Vendas realizadas", dashboard?.total_vendas ?: 0) }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Gerenciamento", style = MaterialTheme.typography.titleLarge)
        }
        items(resourceNames.filter { it != "usuarios" || state.user?.cargo == "admin" }) { resource ->
            val spec = resourceSpecs.getValue(resource)
            OutlinedButton(
                onClick = { onOpen(resource) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("${spec.title}  ›", modifier = Modifier.padding(vertical = 5.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun StatCard(label: String, value: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ModuleScreen(
    resource: String,
    state: AppUiState,
    vehicleSort: String,
    isAdmin: Boolean,
    openNew: Boolean,
    onNewConsumed: () -> Unit,
    onRefresh: () -> Unit,
    onSave: (Long?, Record) -> Unit,
    onDelete: (Long) -> Unit
) {
    val spec = resourceSpecs[resource]
    var editing by remember { mutableStateOf<Record?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Record?>(null) }

    LaunchedEffect(openNew) {
        if (openNew) {
            editing = null
            showForm = true
            onNewConsumed()
        }
    }
    if (spec == null) {
        Text("Módulo não encontrado.", modifier = Modifier.padding(24.dp))
        return
    }
    if (resource == "usuarios" && !isAdmin) {
        Text("Apenas administradores podem gerenciar funcionários.", modifier = Modifier.padding(24.dp))
        return
    }
    if (showForm) {
        RecordEditor(
            resource = resource,
            spec = spec,
            record = editing,
            lookups = state.lookups,
            isAdmin = isAdmin,
            onDismiss = { showForm = false },
            onSave = { record ->
                onSave(editing?.id, record)
                showForm = false
            }
        )
    }
    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir registro?") },
            text = { Text("Esta ação não pode ser desfeita. Se houver dados relacionados, a API recusará a exclusão.") },
            confirmButton = {
                Button(onClick = {
                    record.id?.let(onDelete)
                    deleting = null
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${state.records.size} registro(s)", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onRefresh) { Text("Atualizar") }
        }
        if (state.loading && state.records.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (state.records.isEmpty()) {
            Text("Nenhum registro cadastrado.", modifier = Modifier.padding(top = 24.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                if (resource == "veiculos" && vehicleSort == "preco") {
                    state.records.sortedBy { it.preco }
                } else state.records,
                key = { it.id ?: it.hashCode().toLong() }
            ) { record ->
                RecordCard(
                    resource = resource,
                    record = record,
                    canEdit = !spec.createOnly,
                    canDelete = !spec.createOnly,
                    onEdit = { editing = record; showForm = true },
                    onDelete = { deleting = record }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun RecordCard(
    resource: String,
    record: Record,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (resource == "veiculos" && !record.imagem_url.isNullOrBlank()) {
                AsyncImage(
                    model = record.imagem_url,
                    contentDescription = record.titleFor(resource),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(170.dp)
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(record.titleFor(resource), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(record.subtitleFor(resource), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            when (resource) {
                "veiculos" -> Text(
                    "${record.status?.replaceFirstChar { it.uppercase() } ?: "Disponível"} · ${record.preco?.asCurrency() ?: "—"}",
                    color = MaterialTheme.colorScheme.primary
                )
                "vendas" -> Text("${record.data_venda ?: "—"} · ${record.valor_venda?.asCurrency() ?: "—"}")
                "clientes" -> Text(record.telefone.orEmpty())
            }
            if (canEdit || canDelete) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canEdit) TextButton(onClick = onEdit) { Text("Editar") }
                    if (canDelete) TextButton(onClick = onDelete) { Text("Excluir") }
                }
            }
        }
    }
}

@Composable
private fun RecordEditor(
    resource: String,
    spec: ResourceSpec,
    record: Record?,
    lookups: Map<String, List<Record>>,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (Record) -> Unit
) {
    val values = remember(record?.id) {
        mutableStateMapOf<String, String>().apply {
            spec.fields.forEach { field -> put(field.key, record?.fieldValue(field.key).orEmpty()) }
        }
    }
    val fields = if (resource == "usuarios") {
        spec.fields.map { if (it.key == "senha") it.copy(required = record == null) else it }
    } else spec.fields
    val visibleFields = fields.filter { it.key != "usuario_id" || isAdmin }
    val missing = visibleFields.any { it.required && values[it.key].isNullOrBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (record == null) "Novo ${spec.title.lowercase().removeSuffix("s")}" else "Editar registro") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                visibleFields.forEach { field ->
                    val choices = choicesFor(field.key, lookups)
                    if (choices.isNotEmpty()) {
                        ChoiceField(
                            label = field.label,
                            value = values[field.key].orEmpty(),
                            choices = choices,
                            onSelect = { values[field.key] = it.toString() }
                        )
                    } else {
                        OutlinedTextField(
                            value = values[field.key].orEmpty(),
                            onValueChange = { values[field.key] = it },
                            label = { Text(field.label) },
                            singleLine = field.key != "endereco" && field.key != "descricao",
                            visualTransformation = if (field.key == "senha") PasswordVisualTransformation()
                            else VisualTransformation.None,
                            keyboardOptions = if (field.key in setOf("ano", "quilometragem", "preco", "valor_venda", "cpf")) {
                                KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            } else KeyboardOptions.Default,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (resource == "vendas") {
                    Text("O veículo será marcado como vendido após a confirmação.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(values.toRecord(resource)) },
                enabled = !missing
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ChoiceField(
    label: String,
    value: String,
    choices: List<Choice>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.value == value }?.label.orEmpty()
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedLabel.isBlank()) "Selecionar $label" else "$label: $selectedLabel")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    onClick = { onSelect(choice.value); expanded = false }
                )
            }
        }
    }
}

private fun choicesFor(field: String, lookups: Map<String, List<Record>>): List<Choice> {
    if (field == "cargo") return listOf(Choice("admin", "Administrador"), Choice("vendedor", "Vendedor"))
    val resource = when (field) {
        "marca_id" -> "marcas"
        "cliente_id" -> "clientes"
        "veiculo_id" -> "veiculos"
        "usuario_id" -> "usuarios"
        else -> return emptyList()
    }
    return lookups[resource].orEmpty()
        .filter { field != "veiculo_id" || it.status == "disponivel" }
        .mapNotNull { item ->
            val id = item.id ?: return@mapNotNull null
            val label = when (resource) {
                "marcas" -> item.nome
                "clientes", "usuarios" -> item.nome
                "veiculos" -> listOfNotNull(item.marca_nome, item.modelo, item.ano?.toString()).joinToString(" ")
                else -> null
            }.orEmpty()
            Choice(id.toString(), label)
        }
}

private fun Record.fieldValue(key: String): String? = when (key) {
    "nome" -> nome
    "usuario" -> usuario
    "email" -> email
    "cargo" -> cargo
    "cpf" -> cpf
    "telefone" -> telefone
    "endereco" -> endereco
    "pais_origem" -> pais_origem
    "marca_id" -> marca_id?.toString()
    "modelo" -> modelo
    "ano" -> ano?.toString()
    "cor" -> cor
    "quilometragem" -> quilometragem?.toString()
    "preco" -> preco?.toString()
    "descricao" -> descricao
    "imagem_url" -> imagem_url
    "cliente_id" -> cliente_id?.toString()
    "veiculo_id" -> veiculo_id?.toString()
    "usuario_id" -> usuario_id?.toString()
    "data_venda" -> java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
    "valor_venda" -> valor_venda?.toString()
    "forma_pagamento" -> forma_pagamento
    else -> null
}

private fun Map<String, String>.toRecord(resource: String): Record {
    fun text(key: String): String? = get(key)?.trim()?.takeIf(String::isNotEmpty)
    fun long(key: String): Long? = text(key)?.toLongOrNull()
    fun int(key: String): Int? = text(key)?.toIntOrNull()
    fun decimal(key: String): Double? = text(key)?.replace(',', '.')?.toDoubleOrNull()
    return when (resource) {
        "usuarios" -> Record(
            nome = text("nome"), usuario = text("usuario"), email = text("email"),
            cargo = text("cargo"), senha = text("senha")
        )
        "clientes" -> Record(
            nome = text("nome"), cpf = text("cpf"), telefone = text("telefone"),
            email = text("email"), endereco = text("endereco")
        )
        "marcas" -> Record(nome = text("nome"), pais_origem = text("pais_origem"))
        "veiculos" -> Record(
            marca_id = long("marca_id"), modelo = text("modelo"), ano = int("ano"),
            cor = text("cor"), quilometragem = int("quilometragem"), preco = decimal("preco"),
            descricao = text("descricao"), imagem_url = text("imagem_url")
        )
        "vendas" -> Record(
            cliente_id = long("cliente_id"), veiculo_id = long("veiculo_id"),
            usuario_id = long("usuario_id"), data_venda = text("data_venda"),
            valor_venda = decimal("valor_venda"), forma_pagamento = text("forma_pagamento")
        )
        else -> Record()
    }
}

private fun Record.titleFor(resource: String): String = when (resource) {
    "usuarios" -> nome.orEmpty()
    "clientes" -> nome.orEmpty()
    "marcas" -> nome.orEmpty()
    "veiculos" -> listOfNotNull(marca_nome, modelo, ano?.toString()).joinToString(" ")
    "vendas" -> "${cliente_nome ?: "Cliente"} · ${marca_nome.orEmpty()} ${veiculo_modelo.orEmpty()}"
    else -> nome.orEmpty()
}

private fun Record.subtitleFor(resource: String): String = when (resource) {
    "usuarios" -> "${usuario.orEmpty()} · ${cargo.orEmpty()} · ${email.orEmpty()}"
    "clientes" -> "${cpf.orEmpty()} · ${email.orEmpty()}"
    "marcas" -> pais_origem.orEmpty()
    "veiculos" -> "${cor.orEmpty()} · ${quilometragem ?: 0} km"
    "vendas" -> "Vendedor: ${vendedor_nome.orEmpty()} · ${forma_pagamento.orEmpty()}"
    else -> ""
}

private fun Double.asCurrency(): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(this)

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    onUpdate: (String?, Boolean?, Boolean?, String?) -> Unit
) {
    var displayName by remember(settings.displayName) { mutableStateOf(settings.displayName) }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Preferências salvas neste dispositivo.", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Nome de exibição") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { onUpdate(displayName.trim(), null, null, null) }) {
            Text("Salvar nome")
        }
        PreferenceSwitch("Tema escuro", settings.darkTheme) {
            onUpdate(null, it, null, null)
        }
        PreferenceSwitch("Preferir notificações", settings.notifications) {
            onUpdate(null, null, it, null)
        }
        Text("Ordenação dos veículos", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onUpdate(null, null, null, "recentes") }) {
                Text(if (settings.vehicleSort == "recentes") "✓ Recentes" else "Recentes")
            }
            OutlinedButton(onClick = { onUpdate(null, null, null, "preco") }) {
                Text(if (settings.vehicleSort == "preco") "✓ Menor preço" else "Menor preço")
            }
        }
    }
}

@Composable
private fun PreferenceSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
