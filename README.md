# AI-Powered Company Research Platform

This project is a full-stack AI-driven platform for company research and information extraction. It combines React frontend, Spring Boot backend with JWT-based auth, LangGraph AI agent using FastAPI and Gemini/Tavily APIs, all containerized using Docker and orchestrated via Docker Compose.

**Deployable on render.com as well**

**Access at: [Company Research AI Platform](https://react-frontend-k26s.onrender.com/)**

**RESEARCH NOT WORKING IN DEMO SITE, DUE TO SOME RENDER SIDE ISSUE, IN LIVE DEMO LANGGRAPH AI AGENT IS NOT WARMING UP AFTER INACTIVITY, WILL FIX IT IN FUTURE**

**Service on Render scales down or goes offline automatically after 15mins of inactivity to save resources. If any user is logging after all services are down, user has to wait for extra amount of time initially so that services can come online automatically. For e.g. if user access UI application, it takes some time, than if any API is hit backend services takes around 40 seconds to come online and similarly AI agent requires some time. All this wait will happen only for the first time.**

## Architecture Overview

**Diagrams**

![image](https://github.com/user-attachments/assets/7aba81cb-f606-45d5-bf8c-da38a73bad3a)

<br/>

![image](https://github.com/user-attachments/assets/e61c9352-733c-4152-a06c-872ef714efe2)

**Components :**

1. <ins>Frontend (React)</ins>

- Provides UI for signup, login, and research input.
- Stores JWT token on login.
- Calls backend /api/analyze endpoint with token.

Refer [INSIGHT_COMPASS_RESEARCHER_APP README FILE](https://github.com/deepgahlot11/company_research_platform/blob/main/insight-compass-researcher-app/README.md)

2. <ins>Backend (Spring Boot)</ins>

- Handles authentication (/api/auth/signup, /api/auth/login) and issues JWT.
- Secures /api/analyze using JWT.
- Forwards requests to the LangGraph AI agent after validating token.
- Stores user details in PostgreSQL DB.

3. <ins>LangGraph AI Agent (FastAPI + LangGraph)</ins>

- Exposes /analyze endpoint to accept company name, schema, and notes.
- Gemini-2.5-Flash API for summarization and semantic extraction.
- Tavily API for real-time data gathering.
- Uses LangGraph workflows to extract structured company information based on a dynamic schema.

Refer [COMPANY_RESEARCHER AI AGENT README FILE](https://github.com/deepgahlot11/company_research_platform/blob/main/company-researcher/README.md) for more details.

4. <ins>Database (PostgreSQL)</ins>

- Stores registered users and their details.

**Data Flow**

1. User Signup/Login (React → Spring)

- User registers/logins from the React app.
- JWT token is stored in browser session storage.

2. Authorized Analyze Request (React → Spring /api/analyze)

- JWT is attached in the header.
- Spring backend validates the token and forwards the data securely to the AI agent.

3. AI-Powered Extraction (Spring → FastAPI /analyze)

- FastAPI LangGraph Agent processes the company input using Gemini & Tavily.
- Extracted structured data is returned to Spring, and then to the frontend.

**Dockerized Setup**

Directory Structure

- bash
- CopyEdit
- /frontend -> React app with UI and API integration
- /backend -> Spring Boot app with auth and forwarding logic
- /agent -> FastAPI LangGraph AI agent
- /docker-compose.yml

Dockerized Services

- frontend - Node + React
- backend - Spring Boot + PostgreSQL
- agent - Python + FastAPI + LangGraph + Gemini/Tavily
- postgres - PostgreSQL official image

Setup Instructions

- bash
- CopyEdit
- Build and run all containers
- docker-compose up --build

Once running:

- React app: http://localhost:8080
- Spring backend: http://localhost:8085
- LangGraph agent: http://localhost:8000
- Postgres DB: localhost:5432

**Authentication & Security**

- JWT-based authentication via Spring Security.
- React stores token in sessionStorage as authToken.
- Spring filters all protected routes using a custom JwtAuthFilter.
- Calls to the AI agent are made only from Spring backend and authorized using a shared secret header (x-internal-key).

**Sample Analyze Payload**

JSON with Simple Extraction schema

```
{
  "company": "OpenAI",
  "extraction_schema": {
    "founded_year": "int",
    "headquarters": "str",
    "industry": "str"
  },
  "user_notes": "Include details about recent partnerships"
}
```

JSON with Complex Extraction schema

```
{
  "type": "object",
  "properties": {
    "title": { "type": "string" },
    "company_name": { "type": "string" },
    "company_size": { "type": "integer" },
    "founding_year": { "type": "integer" },
    "founder_names": { "type": "string" },
    "product_description": { "type": "string" },
    "funding_summary": { "type": "string" },
    "controversies": { "type": "string" },
    "acquisitions": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "company": { "type": "string" },
          "year": { "type": "integer" }
        },
        "required": ["company", "year"]
      }
    },
    "main_products": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "description": {"type": "string"},
                    "launch_date": {"type": "string"},
                    "current_status": {"type": "string"}
                }
            }
        },
        "services": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "description": {"type": "string"},
                    "target_market": {"type": "string"}
                }
            }
        }
  }
}
```

**APIs**

```
Method	Endpoint	            Description
POST	/api/auth/signup	    Register user
POST	/api/auth/login	        Login user, returns JWT
POST	/api/analyze	        Research company (JWT needed)
POST	/analyze (agent)	    Called internally by backend
```

**Technologies**

- Frontend: React, TypeScript
- Backend: Spring Boot, JWT, PostgreSQL
- AI Agent: FastAPI, LangGraph, Gemini 2.5 Flash, Tavily
- Infra: Docker, Docker Compose

**Future Enhancements**

- Can be configured in a way to deploy based on Microservices architecture, so as to scale up and down based on requirement. It helps in saving costs.
- Research, Extraction and Reflection status can be displayed on the frontend with additional websocket and APIs implementation.
- Add MongoDB for storing past analysis.
- Add role-based access control.
- Allow user to customize model used (OpenAI, Gemini, etc.).

## How to run the application?

**Option 1 - Easy setup (Recommended)**

- Make sure Docker is installed on your machine [Download Docker Desktop](https://www.docker.com/products/docker-desktop/)
- Once Docker is installed, run on command line or terminal

```
docker compose up --build
```

- Open any browser and access http://localhost:8080

**Option 2 - Complex, customizable setup (NOT Recommended)**

- Setup postgreSQL DB, create database = user-management, username = testuser_readwrite, password = root
- In Project - 'management' which is Spring boot code, run below commands, make sure application yaml has correct DB details

```
mvn clean install
mvn spring-boot:run
```

- In Project - 'insight-compass-researcher-app' which is frontend react code, run below commands

```
npm i
npm run dev
```

- Run these commands in terminal to run Langraph agent

```
curl -LsSf https://astral.sh/uv/install.sh | sh
cp .env.example .env
uv pip install -e .

cd src
TAVILY_API_KEY=<TAVILY_API_KEY_VALUE> GOOGLE_API_KEY=<GOOGLE_API_KEY_VALUE> PYTHONPATH=src uvicorn agent.main:app --host 0.0.0.0 --port 8000 --reload
```

- Open any browser and access http://localhost:8080
