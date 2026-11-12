package com.example.concessionaria.domain

data class Record(
    val id: Long? = null,
    val nome: String? = null,
    val usuario: String? = null,
    val email: String? = null,
    val cargo: String? = null,
    val senha: String? = null,
    val cpf: String? = null,
    val telefone: String? = null,
    val endereco: String? = null,
    val pais_origem: String? = null,
    val marca_id: Long? = null,
    val marca_nome: String? = null,
    val modelo: String? = null,
    val ano: Int? = null,
    val cor: String? = null,
    val quilometragem: Int? = null,
    val preco: Double? = null,
    val descricao: String? = null,
    val status: String? = null,
    val imagem_url: String? = null,
    val cliente_id: Long? = null,
    val veiculo_id: Long? = null,
    val usuario_id: Long? = null,
    val data_venda: String? = null,
    val valor_venda: Double? = null,
    val forma_pagamento: String? = null,
    val cliente_nome: String? = null,
    val veiculo_modelo: String? = null,
    val vendedor_nome: String? = null
)

data class Dashboard(
    val total_veiculos: Int = 0,
    val veiculos_disponiveis: Int = 0,
    val veiculos_vendidos: Int = 0,
    val total_clientes: Int = 0,
    val total_vendas: Int = 0
)

data class Session(
    val token: String,
    val usuario: Record
)
