# JobFlow

A RESTful project and task management API built with **Java and Spring Boot**.

JobFlow is a backend application designed to manage projects and their associated tasks, with support for task priorities, due dates, status transitions, filtering, pagination, validation, exception handling, and automated testing.

The project was built as a portfolio project with a focus on clean architecture, REST API design, domain logic, and testability.

---

## Features

### Projects

* Create projects
* Retrieve a project by ID
* Retrieve projects with pagination
* Update projects
* Delete projects

### Tasks

* Create tasks associated with a project
* Retrieve a task by ID
* Retrieve all tasks belonging to a project
* Update tasks
* Delete tasks
* Update task status
* Assign task priorities
* Set task due dates
* Filter tasks by status and priority
* Paginate task results

### Business Rules

JobFlow implements controlled task status transitions:

```text
TODO → IN_PROGRESS → DONE
```

Invalid transitions are rejected.

For example:

```text
TODO → DONE
IN_PROGRESS → TODO
DONE → TODO
DONE → IN_PROGRESS
```

These transitions result in a `400 Bad Request`.

---

## Tech Stack

| Technology        | Purpose                         |
| ----------------- | ------------------------------- |
| Java 21           | Programming language            |
| Spring Boot 4.1.1 | Application framework           |
| Spring Web        | REST API                        |
| Spring Data JPA   | Persistence layer               |
| Hibernate         | ORM                             |
| PostgreSQL 17     | Relational database             |
| Docker Compose    | Database environment            |
| Maven             | Build and dependency management |
| JUnit 5           | Testing                         |
| Mockito           | Unit testing and mocking        |
| AssertJ           | Test assertions                 |

---

## Architecture

The application follows a layered architecture:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
PostgreSQL
```

### Controller

Responsible for:

* HTTP endpoints
* Request/response handling
* Request validation
* HTTP status codes

### Service

Contains the application's business logic, including:

* Project/task relationships
* Task status transitions
* Task filtering
* Business validations
* Entity updates

### Repository

Uses Spring Data JPA to communicate with PostgreSQL.

### Exception Handling

The API uses custom exceptions and centralized error handling to provide consistent error responses.

---

## API

Base URL:

```text
http://localhost:8080/api
```

### Projects

| Method   | Endpoint         | Description       |
| -------- | ---------------- | ----------------- |
| `POST`   | `/projects`      | Create a project  |
| `GET`    | `/projects`      | Get projects      |
| `GET`    | `/projects/{id}` | Get project by ID |
| `PUT`    | `/projects/{id}` | Update project    |
| `DELETE` | `/projects/{id}` | Delete project    |

### Tasks

| Method   | Endpoint                      | Description        |
| -------- | ----------------------------- | ------------------ |
| `POST`   | `/projects/{projectId}/tasks` | Create a task      |
| `GET`    | `/projects/{projectId}/tasks` | Get project tasks  |
| `GET`    | `/tasks/{id}`                 | Get task by ID     |
| `PUT`    | `/tasks/{id}`                 | Update task        |
| `DELETE` | `/tasks/{id}`                 | Delete task        |
| `PATCH`  | `/tasks/{id}/status`          | Update task status |

---

## Task Filtering & Pagination

Tasks can be filtered by status and priority.

Example:

```http
GET /api/projects/1/tasks?status=IN_PROGRESS&priority=HIGH
```

Pagination is supported through Spring's `Pageable` abstraction.

Example:

```http
GET /api/projects/1/tasks?page=0&size=10
```

The API returns paginated data including metadata such as the current page, page size, total elements, and total pages.

---

## Example

### Create a Project

```http
POST /api/projects
Content-Type: application/json
```

```json
{
  "name": "Website Redesign",
  "description": "Redesign the company's website"
}
```

Response:

```json
{
  "id": 1,
  "name": "Website Redesign",
  "description": "Redesign the company's website"
}
```

---

### Create a Task

```http
POST /api/projects/1/tasks
Content-Type: application/json
```

```json
{
  "title": "Implement authentication",
  "description": "Add authentication to the API",
  "priority": "HIGH",
  "dueDate": "2026-10-15"
}
```

Response:

```json
{
  "id": 1,
  "projectId": 1,
  "title": "Implement authentication",
  "description": "Add authentication to the API",
  "status": "TODO",
  "priority": "HIGH",
  "dueDate": "2026-10-15"
}
```

---

### Update Task Status

```http
PATCH /api/tasks/1/status
Content-Type: application/json
```

```json
{
  "status": "IN_PROGRESS"
}
```

Response:

```json
{
  "id": 1,
  "projectId": 1,
  "title": "Implement authentication",
  "description": "Add authentication to the API",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "dueDate": "2026-10-15"
}
```

---

## Validation & Error Handling

The API validates incoming requests and returns structured error responses.

Example validation error:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": {
    "title": "must not be blank"
  }
}
```

Example resource-not-found response:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Task with id 999 was not found"
}
```

Invalid task status transitions are also handled explicitly:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Cannot change task status from TODO to DONE"
}
```

---

## Testing

The project includes unit and integration tests covering controllers, services, repositories, validation, business rules, and persistence.

Current test suite:

```text
Tests run: 62
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

Run the complete test suite with:

```powershell
.\mvnw.cmd clean test
```

---

## Running Locally

### Requirements

Make sure you have installed:

* Java 21
* Docker
* Git

Maven does not need to be installed globally because the project includes the Maven Wrapper.

---

### 1. Clone the repository

```bash
git clone https://github.com/heidenreich20/JobFlow.git
cd JobFlow
```

---

### 2. Start PostgreSQL

Start the PostgreSQL container using Docker Compose:

```bash
docker compose up -d
```

The application expects the following database configuration:

```text
Database: jobflow
Username: jobflow
Password: jobflow_password
Port: 5432
```

---

### 3. Run the application

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The API will be available at:

```text
http://localhost:8080
```

---

### 4. Stop the database

When finished:

```bash
docker compose down
```

---

## Project Structure

```text
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── pablo/
│   │           └── jobflow/
│   │               ├── project/
│   │               │   ├── Project.java
│   │               │   ├── ProjectController.java
│   │               │   ├── ProjectRepository.java
│   │               │   ├── ProjectService.java
│   │               │   └── ...
│   │               │
│   │               ├── task/
│   │               │   ├── Task.java
│   │               │   ├── TaskController.java
│   │               │   ├── TaskRepository.java
│   │               │   ├── TaskService.java
│   │               │   ├── TaskSpecifications.java
│   │               │   └── ...
│   │               │
│   │               └── JobflowApplication.java
│   │
│   └── resources/
│       └── application.properties
│
└── test/
    └── java/
        └── com/
            └── pablo/
                └── jobflow/
                    ├── project/
                    └── task/
```

---

## Design Decisions

### Explicit Status Transitions

Task status changes are handled through a dedicated endpoint rather than allowing arbitrary status changes through the regular task update endpoint.

This keeps the business rule explicit:

```text
TODO → IN_PROGRESS
IN_PROGRESS → DONE
```

and prevents invalid state changes.

### Specifications for Filtering

Task filtering uses Spring Data JPA `Specification`s, allowing multiple filters to be composed without creating a large number of repository methods.

For example:

```text
project + status + priority
```

can be combined dynamically in a single query.

### DTOs

The API uses request and response DTOs instead of exposing JPA entities directly.

This keeps the API contract separate from the persistence model and makes future changes easier.

---

## Future Improvements

Possible future features include:

* Authentication and authorization
* User accounts
* Project ownership
* Task assignment
* Task comments
* Task labels/tags
* Project members
* Soft deletes
* Audit history
* OpenAPI / Swagger documentation
* Dockerized application
* CI/CD with GitHub Actions
* Testcontainers for database integration tests
* Production configuration profiles
* API rate limiting
* Improved PATCH semantics

---

## Project Status

**Current status: Functional MVP**

The core project and task management functionality is implemented and covered by automated tests.

The project is currently focused on demonstrating backend development skills with Java, Spring Boot, PostgreSQL, REST APIs, automated testing, and clean separation of responsibilities.

---

## License

This project is currently intended as a personal portfolio project.
