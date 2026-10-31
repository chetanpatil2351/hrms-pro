# Changelog

All notable changes to the HRMS Pro project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]
### Added
- Repository initialization with Git and root .gitignore.
- Full-stack monorepo scaffolding:
  - ackend/: Java 21 + Spring Boot 3.3.4 with JPA, PostgreSQL, Flyway, JJWT, and standard API response envelope / exception handler.
  - web/: React 18 + Vite + Tailwind CSS + Lucide React + Recharts + React Router.
  - mobile/: React Native + Expo + Expo Router + SecureStore + Camera + Location + SQLite.
- PostgreSQL V1 migration schema with multi-tenant tables (	enants, users, efresh_tokens) and PostgreSQL Row-Level Security (RLS) policies.
- Project tracking and progress documentation in docs/PROJECT_PROGRESS.md.