# 17 - Security Policies

## Input Handling
* API endpoints validate payload structure before executing SQL queries.
* Numeric bounds checking is enforced on all rating inputs (`0.5 <= rating <= 5.0`).
* CORS is explicitly scoped using `flask-cors` to prevent unauthorized cross-origin requests.