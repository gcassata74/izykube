# MySQL Connection Test Demo App

Small Express.js application to verify connectivity to a MySQL instance exposed via a Kubernetes Service.

## Features

- Serves a minimal web UI with a **Test connection** button.
- `/test-connection` endpoint connects to MySQL using service DNS and runs `SELECT 1`.
- Reads connection details from environment variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.
- Logs startup, requests, and connection results.

## Local Development

```bash
cd "demo app"
npm install
DB_HOST=mysql.default.svc.cluster.local \
DB_PORT=3306 \
DB_NAME=testdb \
DB_USER=test \
DB_PASSWORD=pass \
npm start
```

Open http://localhost:3000 and click **Test connection**.

## Configurazione richiesta

L'applicazione legge i parametri di connessione al database dalle seguenti variabili d'ambiente (tutte obbligatorie):

| Variabile      | Descrizione                                                                                         | Esempio                                 |
|----------------|-----------------------------------------------------------------------------------------------------|-----------------------------------------|
| `DB_HOST`      | DNS del Service Kubernetes che espone MySQL. Deve puntare al Service, non ai pod.                   | `mysql`, `mysql.default.svc.cluster.local` |
| `DB_PORT`      | Porta esposta dal Service.                                                                           | `3306`                                   |
| `DB_NAME`      | Nome del database a cui connettersi.                                                                 | `testdb`                                 |
| `DB_USER`      | Utente MySQL autorizzato ad accedere al database.                                                    | `tester`                                 |
| `DB_PASSWORD`  | Password dell'utente MySQL.                                                                          | `supersecret`                            |

Impostare queste variabili nei manifest YAML (Deployment) o nel proprio ambiente di sviluppo prima di avviare l'app. Senza di esse l'app ritorna un errore esplicito e non tenta la connessione.

## Container / Deployment

Set the environment variables in your Deployment manifest so the pod connects to the MySQL Service DNS name. Expose port `3000` (or override via `PORT`).
