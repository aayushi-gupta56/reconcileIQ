# ReconcileIQ

## 1. Prerequisites

Install the following software before setting up the project.

| Software | Required version | Download |
|---|---|---|
| Git | Latest | https://git-scm.com/downloads |
| Java JDK | 21 | https://adoptium.net/ |
| Maven | 3.9+ | https://maven.apache.org/download.cgi |
| Node.js | 20+ | https://nodejs.org/ |
| npm | Included with Node.js | Included with Node.js |
| Docker Desktop | Latest | https://www.docker.com/products/docker-desktop/ |
| Ollama | Latest | https://ollama.com/download |

### Verify installations

Open PowerShell or a terminal and execute:

```bash
git --version
java -version
mvn -version
node -v
npm -v
docker --version
docker compose version
ollama --version
```

Ensure Java 21 is configured as the active JDK.

## 2. Clone the repository

```bash
git clone https://github.com/aayushi-gupta56/reconcileIQ.git
```

Navigate to the project directory:

```bash
cd reconcileIQ
```

Project structure:

```text
reconcileIQ/
│
├── backend/
│   ├── src/
│   │   └── main/
│   │       └── resources/
│   │           ├── application.yml
│   │           └── db/
│   │               └── migration/
│   ├── Dockerfile
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   ├── package.json
│   └── vite.config.ts
│
├── samples/
├── scripts/
├── docker-compose.yml
└── README.md
```

## 3. Configure PostgreSQL

Docker Compose is used to run PostgreSQL locally.

From the project root, execute:

```bash
docker compose up -d postgres
```

The PostgreSQL container is configured with:

| Configuration | Value |
|---|---|
| Database | reconcileiq |
| Username | reconcileiq |
| Password | local_dev_only_change_me |
| Host | localhost |
| Port | 5433 |
| PostgreSQL version | 16 |

Verify the container:

```bash
docker ps
```

Check PostgreSQL readiness:

```bash
docker exec reconcileiq-postgres pg_isready -U reconcileiq -d reconcileiq
```

Flyway automatically executes database migrations when the backend starts and seed data is already pushed upon application start.

Migration scripts are located at:

```text
backend/src/main/resources/db/migration/
```

No manual table creation is required.

## 4. Configure backend environment variables

Navigate to the backend directory:

```bash
cd backend
```

The backend reads configuration from environment variables with local development defaults defined in `application.yml`.

### Environment variables

| Variable | Local development value | Description |
|---|---|---|
| SPRING_DATASOURCE_URL | jdbc:postgresql://localhost:5433/reconcileiq | PostgreSQL JDBC URL |
| DB_USERNAME | reconcileiq | Database username |
| DB_PASSWORD | local_dev_only_change_me | Database password |
| PAYPAL_BASE_URL | https://api-m.sandbox.paypal.com | PayPal Sandbox API URL |
| PAYPAL_CLIENT_ID | Your PayPal Sandbox Client ID | PayPal API client ID |
| PAYPAL_CLIENT_SECRET | Your PayPal Sandbox Secret | PayPal API secret |
| RECONCILEIQ_DATA_SOURCE | DEMO_SCENARIOS | Reconciliation data source |
| RECONCILEIQ_AI_PROVIDER | OLLAMA | AI inference provider |
| RECONCILEIQ_AI_BASE_URL | http://localhost:11434 | Ollama API URL |
| RECONCILEIQ_AI_MODEL | qwen3:8b | Model name |
| RECONCILEIQ_AI_TIMEOUT_SECONDS | 60 | AI request timeout |
| RECONCILEIQ_AI_API_KEY | Empty for local Ollama | API key if required by the configured provider |
| ZAPIER_WEBHOOK_URL | Empty | Optional Zapier Catch Hook URL |
| RECONCILEIQ_DASHBOARD_URL | http://localhost:5173 | Frontend URL used in notifications |

### Set environment variables in Windows PowerShell

Run these commands in the same terminal from which you will start the backend:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5433/reconcileiq"
$env:DB_USERNAME="reconcileiq"
$env:DB_PASSWORD="local_dev_only_change_me"

$env:PAYPAL_BASE_URL="https://api-m.sandbox.paypal.com"
$env:PAYPAL_CLIENT_ID="YOUR_PAYPAL_CLIENT_ID"
$env:PAYPAL_CLIENT_SECRET="YOUR_PAYPAL_CLIENT_SECRET"

$env:RECONCILEIQ_DATA_SOURCE="DEMO_SCENARIOS"
$env:RECONCILEIQ_AI_PROVIDER="OLLAMA"
$env:RECONCILEIQ_AI_BASE_URL="http://localhost:11434"
$env:RECONCILEIQ_AI_MODEL="qwen3:8b"
$env:RECONCILEIQ_AI_TIMEOUT_SECONDS="120"

$env:RECONCILEIQ_DASHBOARD_URL="http://localhost:5173"
```

For optional Zapier notifications:

```powershell
$env:ZAPIER_WEBHOOK_URL="YOUR_ZAPIER_WEBHOOK_URL"
```

Replace placeholder credentials with your own values.

These PowerShell environment variables apply only to the current terminal session.

### Configure environment variables in IntelliJ IDEA

1. Open the backend project in IntelliJ IDEA.
2. Open Run → Edit Configurations.
3. Select the Spring Boot application configuration.
4. Locate Environment variables.
5. Add the required variables.
6. Save the configuration.
7. Run the Spring Boot application.

## 5. Configure PayPal Sandbox

PayPal Sandbox is used to create and capture test payments.

### Create a Sandbox application

1. Open https://developer.paypal.com/
2. Sign in to your PayPal Developer account.
3. Navigate to Apps & Credentials.
4. Select the Sandbox environment.
5. Create a new REST API application.
6. Copy the Client ID.
7. Copy the Secret.
8. Configure both values as backend environment variables:

```text
PAYPAL_CLIENT_ID=YOUR_SANDBOX_CLIENT_ID
PAYPAL_CLIENT_SECRET=YOUR_SANDBOX_CLIENT_SECRET
PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com
```

### Create a Sandbox buyer account

1. Open the Sandbox Accounts section.
2. Create or select a Personal test account.
3. Note its email address and password.
4. Use these credentials when completing test payments through the application.

Never expose PayPal credentials in frontend code or commit them to GitHub.

## 6. Install and configure Ollama

Ollama runs the local LLM used for AI-assisted payment investigations.

### Start Ollama

Install Ollama from:

https://ollama.com/download

Open a terminal and execute:

```bash
ollama serve
```

Keep the Ollama service running.

### Download the Qwen model

Open another terminal:

```bash
ollama pull qwen3:8b
```

The model download may take some time depending on network speed.

### Verify the model

```bash
ollama list
```

Verify that `qwen3:8b` appears in the model list.

Test inference:

```bash
ollama run qwen3:8b
```

Enter a test prompt and verify that the model responds.

Exit the interactive session when finished.

### Verify the Ollama API

Ollama exposes its API locally at:

```text
http://localhost:11434
```

Test the API:

```bash
curl http://localhost:11434/api/tags
```

The backend uses the configured Ollama URL and model to generate investigation reports.

## 7. Start the backend

Navigate to the backend directory:

```bash
cd backend
```

Run the Spring Boot application:

```bash
mvn clean spring-boot:run
```

Alternatively, on Windows, if the Maven wrapper is available:

```powershell
.\mvnw.cmd spring-boot:run
```

The backend starts at:

```text
http://localhost:8080
```

Health endpoints:

```text
http://localhost:8080/actuator/health
http://localhost:8080/api/v1/health
```

Flyway migrations execute automatically during application startup.

Keep the backend terminal running.

## 8. Install and start the frontend

Open another terminal.

Navigate to the frontend directory:

```bash
cd frontend
```

Install dependencies:

```bash
npm install
```

### Configure the frontend API URL

Create a file named `.env.local` inside the `frontend` directory.

Add:

```env
VITE_API_BASE_URL=http://localhost:8080
```

Save the file.

The frontend uses this environment variable to communicate with the backend.

### Start the frontend

```bash
npm run dev
```

Vite normally starts the frontend at:

```text
http://localhost:5173
```

Open the URL in your browser.

## 9. Run the complete application

The following services must be running for the complete local application:

| Service | Command | Address |
|---|---|---|
| PostgreSQL | `docker compose up -d postgres` | localhost:5433 |
| Ollama | `ollama serve` | localhost:11434 |
| Spring Boot | `mvn spring-boot:run` | localhost:8080 |
| React frontend | `npm run dev` | localhost:5173 |

Open:

```text
http://localhost:5173
```

## 10. Run reconciliation

1. Open the ReconcileIQ dashboard.
2. Navigate to the reconciliation section.
3. Load the available demo scenarios or use the configured payment data source.
4. Execute a reconciliation run.
5. Review the reconciliation results.
6. Open individual discrepancy records.
7. Trigger AI investigation for eligible discrepancies.
8. Review the generated investigation summary, possible explanations and recommended checks.
9. Review the investigation history.

The application supports the following reconciliation outcomes:

| Status | Description |
|---|---|
| MATCHED | Expected and actual payment details match |
| MISSING_PAYMENT | No corresponding payment was found |
| AMOUNT_MISMATCH | Expected and actual amounts differ |
| CURRENCY_MISMATCH | Expected and actual currencies differ |
| DUPLICATE_PAYMENT | Multiple payments are associated with an order |
| REFERENCE_MISMATCH | Payment reference does not match the expected reference |
| UNEXPECTED_PAYMENT | A payment has no corresponding merchant order |

AI investigations are advisory. All findings require human review.

## 11. Configure Zapier notifications (Optional)

Zapier can send email notifications when a reconciliation run completes.

### Create a Zapier Catch Hook

1. Open https://zapier.com/
2. Create a new Zap.
3. Select Webhooks by Zapier as the trigger.
4. Select Catch Hook.
5. Copy the generated webhook URL.
6. Configure it in the backend environment:

```text
ZAPIER_WEBHOOK_URL=YOUR_ZAPIER_CATCH_HOOK_URL
```

7. Configure the desired email action in Zapier.
8. Test the webhook.
9. Run a reconciliation to verify that the notification is triggered.

The public dashboard URL can be configured using:

```text
RECONCILEIQ_DASHBOARD_URL=http://localhost:5173
```

For hosted deployments, use the deployed frontend URL instead.

Leaving `ZAPIER_WEBHOOK_URL` empty disables notifications.

## 12. Build the backend

From the backend directory:

```bash
mvn clean package
```

The generated JAR is available under:

```text
backend/target/
```

## 13. Build the frontend

From the frontend directory:

```bash
npm install
npm run build
```

The production build is generated under:

```text
frontend/dist/
```

## 14. Deploy the application on Render

### Backend deployment

1. Push the project to GitHub.
2. Open https://render.com/
3. Create a new Web Service.
4. Connect the GitHub repository.
5. Configure:

| Setting | Value |
|---|---|
| Service name | reconcileiq-backend |
| Root directory | backend |
| Runtime | Docker |
| Dockerfile path | Dockerfile |
| Branch | main |

6. Add the backend environment variables.

Example hosted configuration:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR_NEON_HOST/neondb?sslmode=require
DB_USERNAME=YOUR_NEON_USERNAME
DB_PASSWORD=YOUR_NEON_PASSWORD

PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com
PAYPAL_CLIENT_ID=YOUR_PAYPAL_CLIENT_ID
PAYPAL_CLIENT_SECRET=YOUR_PAYPAL_CLIENT_SECRET

RECONCILEIQ_DATA_SOURCE=DEMO_SCENARIOS
RECONCILEIQ_AI_PROVIDER=OLLAMA
RECONCILEIQ_AI_BASE_URL=YOUR_REACHABLE_AI_INFERENCE_ENDPOINT
RECONCILEIQ_AI_MODEL=qwen3:8b
RECONCILEIQ_AI_TIMEOUT_SECONDS=120

RECONCILEIQ_DASHBOARD_URL=https://reconcileiq-frontend.onrender.com
ZAPIER_WEBHOOK_URL=YOUR_ZAPIER_WEBHOOK_URL
```

7. Do not manually set the `PORT` variable. Render supplies it.
8. Deploy the service.
9. Check the deployment logs.
10. Verify the backend health endpoint.

```text
https://reconcileiq-l2wi.onrender.com/api/v1/health
```

### Frontend deployment

1. In Render, create a new Static Site.
2. Connect the same GitHub repository.
3. Configure:

| Setting | Value |
|---|---|
| Site name | reconcileiq-frontend |
| Root directory | frontend |
| Build command | npm ci && npm run build |
| Publish directory | dist |
| Branch | main |

4. Add the environment variable:

```env
VITE_API_BASE_URL=https://reconcileiq-l2wi.onrender.com
```

5. Deploy the Static Site.
6. Open the deployed frontend.

```text
https://reconcileiq-frontend.onrender.com
```

### Database deployment

The hosted application uses Neon PostgreSQL.

1. Open https://neon.tech/
2. Create a PostgreSQL project.
3. Copy the database connection details.
4. Configure the Render backend environment variables:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR_NEON_HOST/neondb?sslmode=require
DB_USERNAME=YOUR_NEON_USERNAME
DB_PASSWORD=YOUR_NEON_PASSWORD
```

5. Save the environment variables.
6. Redeploy the backend.
7. Verify that Flyway migrations execute successfully.

## 15. Environment variable reference

### Backend

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/reconcileiq
DB_USERNAME=reconcileiq
DB_PASSWORD=local_dev_only_change_me

PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com
PAYPAL_CLIENT_ID=
PAYPAL_CLIENT_SECRET=

RECONCILEIQ_DATA_SOURCE=DEMO_SCENARIOS

RECONCILEIQ_AI_PROVIDER=OLLAMA
RECONCILEIQ_AI_BASE_URL=http://localhost:11434
RECONCILEIQ_AI_MODEL=qwen3:8b
RECONCILEIQ_AI_TIMEOUT_SECONDS=120
RECONCILEIQ_AI_API_KEY=

ZAPIER_WEBHOOK_URL=
RECONCILEIQ_DASHBOARD_URL=http://localhost:5173
```

### Frontend

```env
VITE_API_BASE_URL=http://localhost:8080
```

### Important configuration notes

- Backend environment variables must be configured in the backend environment.
- Frontend variables must use the `VITE_` prefix.
- Frontend environment variables are included in the browser build and must never contain secrets.
- Do not commit `.env`, `.env.local` or API credentials to GitHub.
- Restart the relevant service after changing environment variables.
- Use the hosted database connection string only in the backend.
- A hosted backend cannot access Ollama through `localhost:11434` on your personal laptop. Hosted AI inference requires an inference service reachable from Render.
- Keep PayPal credentials and AI provider API keys on the backend.

## 16. Troubleshooting

### PostgreSQL connection failure

Verify that Docker Desktop is running:

```bash
docker ps
```

Restart PostgreSQL:

```bash
docker compose restart postgres
```

### Backend startup failure

Check:

```bash
java -version
mvn -version
```

Verify database credentials and the JDBC URL.

### Ollama connection failure

Verify Ollama is running:

```bash
ollama list
```

Start the service:

```bash
ollama serve
```

Verify the API:

```bash
curl http://localhost:11434/api/tags
```

### Model not found

Pull the model:

```bash
ollama pull qwen3:8b
```

### Frontend cannot connect to backend

Verify:

```env
VITE_API_BASE_URL=http://localhost:8080
```

Restart the Vite development server after changing `.env.local`.

Check the backend logs and browser console for API and CORS errors.

### Flyway migration failure

Check the backend logs for the failed migration.

Migration scripts are located at:

```text
backend/src/main/resources/db/migration/
```

Do not modify an already-applied migration. Create a new versioned migration instead.

## 17. Stop the application

Stop the frontend and backend using:

```text
Ctrl + C
```

Stop the Ollama server using:

```text
Ctrl + C
```

Stop the PostgreSQL container:

```bash
docker compose down
```

To remove the local PostgreSQL data volume and reset the database completely:

```bash
docker compose down -v
```

This permanently deletes the local PostgreSQL data stored in the Docker volume.