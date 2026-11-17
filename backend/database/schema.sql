CREATE DATABASE IF NOT EXISTS concessionaria
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE concessionaria;

CREATE TABLE IF NOT EXISTS usuarios (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    usuario VARCHAR(60) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    cargo ENUM('admin', 'vendedor') NOT NULL DEFAULT 'vendedor',
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS clientes (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    cpf CHAR(11) NOT NULL UNIQUE,
    telefone VARCHAR(25) NOT NULL,
    email VARCHAR(190) NULL,
    endereco VARCHAR(255) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS marcas (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(80) NOT NULL UNIQUE,
    pais_origem VARCHAR(80) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS veiculos (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    marca_id BIGINT UNSIGNED NOT NULL,
    modelo VARCHAR(120) NOT NULL,
    ano SMALLINT UNSIGNED NOT NULL,
    cor VARCHAR(40) NOT NULL,
    quilometragem INT UNSIGNED NOT NULL DEFAULT 0,
    preco DECIMAL(12, 2) NOT NULL,
    descricao TEXT NULL,
    status ENUM('disponivel', 'vendido') NOT NULL DEFAULT 'disponivel',
    imagem_url VARCHAR(2048) NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_veiculos_marcas FOREIGN KEY (marca_id)
        REFERENCES marcas (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_veiculos_preco CHECK (preco >= 0),
    INDEX idx_veiculos_status (status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS vendas (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    cliente_id BIGINT UNSIGNED NOT NULL,
    veiculo_id BIGINT UNSIGNED NOT NULL UNIQUE,
    usuario_id BIGINT UNSIGNED NOT NULL,
    data_venda DATE NOT NULL,
    valor_venda DECIMAL(12, 2) NOT NULL,
    forma_pagamento VARCHAR(40) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vendas_clientes FOREIGN KEY (cliente_id)
        REFERENCES clientes (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_vendas_veiculos FOREIGN KEY (veiculo_id)
        REFERENCES veiculos (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_vendas_usuarios FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_vendas_valor CHECK (valor_venda >= 0),
    INDEX idx_vendas_data (data_venda)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS api_sessoes (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expira_em DATETIME NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sessoes_usuarios FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON UPDATE CASCADE ON DELETE CASCADE,
    INDEX idx_sessoes_expiracao (expira_em)
) ENGINE=InnoDB;
