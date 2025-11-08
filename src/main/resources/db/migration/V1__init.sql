-- Tabela de usuários
-- Nota: Para H2 (dev), Flyway pode precisar de ajustes ou usar hibernate.ddl-auto=update
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nome VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Índice para busca por email
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Tabela de boletos
CREATE TABLE IF NOT EXISTS boletos (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
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
    CONSTRAINT fk_boletos_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_status CHECK (status IN ('PENDENTE', 'PAGO', 'VENCIDO'))
);

-- Índices para consultas frequentes
CREATE INDEX IF NOT EXISTS idx_boletos_user_id ON boletos(user_id);
CREATE INDEX IF NOT EXISTS idx_boletos_status ON boletos(status);
CREATE INDEX IF NOT EXISTS idx_boletos_vencimento ON boletos(vencimento);
CREATE INDEX IF NOT EXISTS idx_boletos_user_status ON boletos(user_id, status);

-- Usuário dummy para testes (senha: password123 - hash BCrypt)
-- Nota: Pode falhar se usuário já existir (ok para desenvolvimento)
INSERT INTO users (email, password, nome) 
SELECT 'admin@venceja.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Admin VenceJa'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@venceja.com');