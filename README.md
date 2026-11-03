# ReconcileIQ starter

ReconcileIQ is a payment reconciliation and AI investigation project.

## Prerequisites
- Java 21
- Maven 3.9+
- Node.js 20+ (npm included)
- Docker Desktop
- Git

## Start PostgreSQL
From this directory:
```powershell
docker compose up -d postgres
```

## Start the backend
```powershell
cd backend
mvn spring-boot:run
```
The backend will be available at http://localhost:8080.
Health check: http://localhost:8080/actuator/health

## Start the frontend
In a second terminal:
```powershell
cd frontend
npm install
npm run dev
```
Open the URL printed by Vite, normally http://localhost:5173.

## Current scope
This is intentionally a small starter scaffold. The first implementation milestone is a working local environment. We will add the database model, CSV import, PayPal Sandbox sync, deterministic matching, and then AI-assisted investigations in that order.

## Security
Never commit API secrets. PayPal and LLM credentials will later be stored in local environment variables, not source code.
