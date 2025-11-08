# VenceJa Core

MVP para gerenciamento de boletos em restaurantes brasileiros - Backend Spring Boot 3.x

## 🚀 Tecnologias

- **Java 17**
- **Spring Boot 3.2+**
- **PostgreSQL** (produção) / **H2** (desenvolvimento)
- **Spring Security + JWT**
- **JPA/Hibernate**
- **Flyway** (migrações)
- **Google Cloud Vision API** (OCR)
- **Firebase Storage** (armazenamento de arquivos)
- **Lombok**
- **Bucket4j** (rate limiting)

## 📋 Pré-requisitos

- Java 17 ou superior
- Maven 3.6+
- PostgreSQL (para produção)
- Conta Google Cloud com Vision API habilitada
- Firebase Storage configurado

## ⚙️ Configuração

### Variáveis de Ambiente

Crie um arquivo `.env` ou configure as variáveis de ambiente:

```bash
# Database
DB_URL=jdbc:postgresql://localhost:5432/venceja
DB_USERNAME=postgres
DB_PASSWORD=senha

# JWT
JWT_SECRET=sua-chave-secreta-min-256-bits-aqui

# Google Cloud Vision
GOOGLE_VISION_KEY=sua-api-key-do-google-vision

# Firebase Storage
FIREBASE_BUCKET=seu-bucket-firebase
FIREBASE_PROJECT_ID=seu-project-id

# Spring Profile
SPRING_PROFILES_ACTIVE=dev  # ou 'prod' para produção
```

### Credenciais Google Cloud

1. Crie um projeto no [Google Cloud Console](https://console.cloud.google.com/)
2. Habilite a **Cloud Vision API**
3. Crie uma conta de serviço e baixe o arquivo JSON de credenciais
4. Configure a variável de ambiente `GOOGLE_APPLICATION_CREDENTIALS` apontando para o arquivo JSON:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/caminho/para/credenciais.json
   ```

### Firebase Storage

1. Crie um projeto no [Firebase Console](https://console.firebase.google.com/)
2. Habilite o **Cloud Storage**
3. Configure as credenciais (mesmo projeto do Google Cloud ou configure separadamente)
4. Defina as variáveis `FIREBASE_BUCKET` e `FIREBASE_PROJECT_ID`

## 🏃‍♂️ Executando a Aplicação

### Desenvolvimento (H2)

```bash
# Instalar dependências
mvn clean install

# Executar aplicação
mvn spring-boot:run

# Ou usando o profile dev explicitamente
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

A aplicação estará disponível em `http://localhost:8080`

### Produção (PostgreSQL)

```bash
# Configurar variáveis de ambiente (ver acima)
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://localhost:5432/venceja
# ... outras variáveis

# Executar
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

## 📚 API Endpoints

### Base URL
```
http://localhost:8080/api/v1
```

### Autenticação

#### POST /auth/login
Login do usuário.

**Request:**
```json
{
  "email": "admin@venceja.com",
  "password": "password123"
}
```

**Response:**
```json
{
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": 1,
      "email": "admin@venceja.com",
      "nome": "Admin VenceJa"
    }
  },
  "message": "Login realizado com sucesso"
}
```

### Boletos

#### POST /boletos/scan
Processa upload de boleto com OCR.

**Headers:**
```
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**Body (FormData):**
- `file`: Arquivo de imagem/PDF do boleto
- `data` (opcional): JSON com dados para edição manual
  ```json
  {
    "fornecedor": "Fornecedor XYZ",
    "valor": 150.50,
    "vencimento": "25/12/2024",
    "codigoBarras": "123456789012345678901234567890123456789012345678",
    "observacoes": "Observações adicionais"
  }
  ```

**Response:**
```json
{
  "data": {
    "id": 1,
    "userId": 1,
    "fornecedor": "Fornecedor XYZ",
    "valor": 150.50,
    "vencimento": "25/12/2024",
    "codigoBarras": "123456789012345678901234567890123456789012345678",
    "status": "PENDENTE",
    "comprovanteUrl": null,
    "semComprovante": false,
    "observacoes": null,
    "createdAt": "2024-01-15 10:30:00",
    "updatedAt": "2024-01-15 10:30:00"
  },
  "message": "Boleto processado com sucesso"
}
```

#### GET /boletos
Lista boletos do usuário autenticado.

**Headers:**
```
Authorization: Bearer {token}
```

**Query Parameters:**
- `page` (default: 0): Número da página
- `size` (default: 10): Tamanho da página
- `status` (opcional): Filtrar por status (PENDENTE, PAGO, VENCIDO)

**Response:**
```json
{
  "data": {
    "content": [...],
    "totalElements": 50,
    "totalPages": 5,
    "number": 0,
    "size": 10
  },
  "message": "Boletos listados com sucesso",
  "meta": {
    "total": 50,
    "page": 0,
    "size": 10
  }
}
```

#### PUT /boletos/{id}/pagar
Marca boleto como pago.

**Headers:**
```
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**Body (FormData):**
- `comprovante` (opcional): Arquivo de comprovante de pagamento
- `semComprovante` (opcional, default: false): Boolean indicando se não há comprovante

**Response:**
```json
{
  "data": {
    "id": 1,
    "status": "PAGO",
    "comprovanteUrl": "https://storage.googleapis.com/...",
    "semComprovante": false,
    ...
  },
  "message": "Boleto marcado como pago"
}
```

## 🧪 Testes

### Usuário de Teste

O sistema inclui um usuário dummy para testes:
- **Email:** `admin@venceja.com`
- **Senha:** `password123`

### Executar Testes

```bash
mvn test
```

## 🚢 Deploy no Railway

### 1. Configurar Railway

```bash
# Instalar Railway CLI
npm i -g @railway/cli

# Login
railway login

# Inicializar projeto
railway init
```

### 2. Configurar Variáveis de Ambiente no Railway

No dashboard do Railway, configure as seguintes variáveis:

- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL` (gerado automaticamente pelo Railway PostgreSQL)
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `GOOGLE_VISION_KEY`
- `FIREBASE_BUCKET`
- `FIREBASE_PROJECT_ID`
- `GOOGLE_APPLICATION_CREDENTIALS` (conteúdo do arquivo JSON ou caminho)

### 3. Deploy

```bash
# Deploy
railway up
```

### 4. Configurar PostgreSQL

Railway cria automaticamente um banco PostgreSQL. As migrações Flyway serão executadas automaticamente na primeira execução.

## 📱 Integração Mobile (React Native)

### Headers Obrigatórios

```javascript
headers: {
  'Authorization': `Bearer ${token}`,
  'Content-Type': 'multipart/form-data' // Para uploads
}
```

### Exemplo de Upload de Boleto

```javascript
const formData = new FormData();
formData.append('file', {
  uri: imageUri,
  type: 'image/jpeg',
  name: 'boleto.jpg'
});

// Opcional: dados para edição manual
formData.append('data', JSON.stringify({
  fornecedor: 'Fornecedor XYZ',
  valor: 150.50,
  vencimento: '25/12/2024'
}));

const response = await fetch('https://api.venceja.com/api/v1/boletos/scan', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
  },
  body: formData
});
```

### Códigos de Erro

- `VALIDATION_ERROR`: Erro de validação nos dados
- `USER_NOT_FOUND`: Usuário não encontrado
- `INVALID_CREDENTIALS`: Credenciais inválidas
- `OCR_FAIL`: Falha no processamento OCR (fallback para entrada manual)
- `INTERNAL_ERROR`: Erro interno do servidor

## 🔒 Segurança

- **JWT Authentication**: Tokens com expiração de 24 horas
- **BCrypt**: Hash de senhas
- **Rate Limiting**: 100 requisições por minuto
- **CORS**: Configurado para localhost:19006 (Expo) e localhost:5173 (Vite)
- **Validation**: Validação de dados com Bean Validation

## 📊 Agendamento

O sistema executa verificações diárias às 9h para boletos próximos do vencimento (3 dias). Os alertas são registrados nos logs (futuro: webhook para Expo).

## 🛠️ Desenvolvimento

### Estrutura do Projeto

```
src/
├── main/
│   ├── java/com/yagodaoud/venceja/
│   │   ├── config/          # Configurações (Security, JWT, CORS, etc.)
│   │   ├── controller/      # Controllers REST
│   │   ├── dto/             # Data Transfer Objects
│   │   ├── entity/          # Entidades JPA
│   │   ├── exception/       # Exception handlers
│   │   ├── repository/      # Repositórios Spring Data
│   │   ├── service/         # Lógica de negócio
│   │   └── VencejaApplication.java
│   └── resources/
│       ├── application.yml  # Configurações da aplicação
│       └── db/migration/    # Migrações Flyway
└── test/                    # Testes
```

## 📝 Licença

Este projeto é um MVP interno.

## 🤝 Contribuindo

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/AmazingFeature`)
3. Commit suas mudanças (`git commit -m 'Add some AmazingFeature'`)
4. Push para a branch (`git push origin feature/AmazingFeature`)
5. Abra um Pull Request
