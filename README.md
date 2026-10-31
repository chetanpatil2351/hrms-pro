# HRMS Pro — Enterprise Commercial-Grade HRMS SaaS

HRMS Pro is an independently developed, production-ready Human Resource Management System supporting multi-tenant cloud SaaS and dedicated enterprise deployments.

## Architecture

- **Backend**: Spring Boot 3.x, Java 21, Spring Security, Spring Data JPA, PostgreSQL, Flyway, JWT, OpenAPI / Swagger
- **Web App**: React, Vite, Tailwind CSS, React Router, Lucide React, Recharts
- **Mobile App**: React Native, Expo, Expo Router, SQLite, SecureStore, Camera, Geofencing
- **Database**: PostgreSQL with Row Level Security (RLS) for complete tenant isolation

## Monorepo Layout

- `backend/`: Java Spring Boot microservice / REST API
- `web/`: Admin, HR, and Employee web portal
- `mobile/`: Cross-platform mobile app for self-service & field attendance
- `docs/`: Technical specifications, architectural decisions, and progress logs

## Setup & Running

Instructions will be populated per module as each phase is implemented and verified.
