-- Migração específica para PostgreSQL (opcional - para usar BIGSERIAL)
-- Esta migração é um exemplo alternativo para PostgreSQL
-- A migração principal V1__init.sql já funciona com ambos os bancos

-- Se preferir usar BIGSERIAL no PostgreSQL, descomente abaixo e ajuste Flyway para usar este arquivo no profile prod
/*
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nome VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);

CREATE TABLE boletos (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    fornecedor VARCHAR(255) NOT NULL,
    valor DECIMAL(10, 2) NOT NULL,
    vencimento DATE NOT NULL,
    codigo_barras VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    comprovante_url TEXT,
    sem_comprovante BOOLEAN DEFAULT FALSE,
    observacoes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_status CHECK (status IN ('PENDENTE', 'PAGO', 'VENCIDO'))
);

CREATE INDEX idx_boletos_user_id ON boletos(user_id);
CREATE INDEX idx_boletos_status ON boletos(status);
CREATE INDEX idx_boletos_vencimento ON boletos(vencimento);
CREATE INDEX idx_boletos_user_status ON boletos(user_id, status);

INSERT INTO users (email, password, nome) 
VALUES ('admin@venceja.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Admin VenceJa')
ON CONFLICT (email) DO NOTHING;
*/
