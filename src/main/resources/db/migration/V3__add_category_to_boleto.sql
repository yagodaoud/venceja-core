-- Adiciona coluna categoria_id na tabela boletos
ALTER TABLE boletos
ADD COLUMN categoria_id BIGINT;

-- Adiciona foreign key (opcional, não obrigatória)
ALTER TABLE boletos
ADD CONSTRAINT fk_boletos_categoria
FOREIGN KEY (categoria_id) REFERENCES categorias(id) ON DELETE SET NULL;

-- Índice para performance
CREATE INDEX IF NOT EXISTS idx_boletos_categoria_id ON boletos(categoria_id);