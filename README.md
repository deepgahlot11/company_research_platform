# AI-Powered Company Research Platform

This project is a full-stack AI-driven platform for company research and information extraction. It combines React frontend, Spring Boot backend with JWT-based auth, LangGraph AI agent using FastAPI and Gemini/Tavily APIs, all containerized using Docker and orchestrated via Docker Compose.

## Architecture Overview

**Diagrams**

![image](https://github.com/user-attachments/assets/7aba81cb-f606-45d5-bf8c-da38a73bad3a)

<br/>

![image](https://github.com/user-attachments/assets/e61c9352-733c-4152-a06c-872ef714efe2)

**Components :**

1. Frontend (React)

- Provides UI for signup, login, and research input.
- Stores JWT token on login.
- Calls backend /api/analyze endpoint with token.

React code credit: lovable.dev
Just 3 Prompts used to create app -
```
1. 
  I need to create an app for Company Researcher, I need to have a login/signup page with minimum details to have first name, last name , email id as user name, new password for signup. For login email id and password are enough. Create dummy endpoints so to easily integrate with backend rest APIs. After login, landing page will be simple where on left hand side pane will take input like gemini and right hand pane will show results. These could  be user inputs -
  
  * company: str - A company to research
  * extraction_schema: Optional[dict] - A JSON schema for the output
  * user_notes: Optional[str] - Any additional notes about the company from the user
  
  If above schema is not input only company name would be sufficient so company name is mandatory as text
  
  For these inputs create dummy endpoint integration with rest API which i will integrate later.

2.
  In place of dummy login integrate /api/auth/login endpoint and for sign up integrate /api/auth/signup endpoints. With current code I am getting Login failed even though after successful login API call I am getting token as
  
  {
      "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkZWVwZ2FobG90MTIzQGdtYWlsLmNvbSIsImlhdCI6MTc1MDY2OTU2OCwiZXhwIjoxNzUwNzA1NTY4fQ.Km-LEtas-MbgqsGPtXrsZnVs_1hly5imr8WKo9DTeezBVYhBTDTOy1-qkrRCTe1mRmRIoTt3tZ8eydrYNmpqaQ"
  }
  
  Complete code if I get this token as login successful and redirect to landing page. Also set the token in session and when logout is clicked remove the token from browser session
3. 
  Landing page request api is http://localhost:8000/analyze & request payload is where company is mandatory, extraction_schema, user_notes is optional. Company name looks good on the top in Right hand pane but below content should be dynamic created based on response json
  
  {
      "company": "Mahindra & Mahindra",
      "extraction_schema": {
        "founded_year": "int",
        "headquarters": "str",
        "industry": "str"
      },
      "user_notes": "Include details about recent partnerships"
    }
  
  Response is something like, but it is dynamic it could have n number of json variables depending on extraction schema
  
  {
      "info": {
          "founded_year": 1945,
          "headquarters": "Mumbai, India",
          "industry": "Consumer Durables"
      }
  }
```

2. Backend (Spring Boot)

- Handles authentication (/api/auth/signup, /api/auth/login) and issues JWT.
- Secures /api/analyze using JWT.
- Forwards requests to the LangGraph AI agent after validating token.
- Stores user details in PostgreSQL DB.

3. LangGraph AI Agent (FastAPI + LangGraph)

- Exposes /analyze endpoint to accept company name, schema, and notes.
- Gemini-2.5-Flash API for summarization and semantic extraction.
- Tavily API for real-time data gathering.
- Uses LangGraph workflows to extract structured company information based on a dynamic schema.

4. Database (PostgreSQL)

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

**Option 2 - Complex, customizable setup**

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
