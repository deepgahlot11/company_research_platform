# Company Research platform - frontend

**React code credit: lovable.dev**

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