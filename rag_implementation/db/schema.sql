-- Schema for the rag_v1 database, exported from the dev database via
-- SHOW CREATE TABLE since there was no committed schema or migration
-- tool (Flyway/Liquibase) anywhere in the project - this was the only
-- source of truth for the database structure up to this point.
--
-- To set up a fresh database:
--   mysql -u root -p -e "CREATE DATABASE rag_v1"
--   mysql -u root -p rag_v1 < db/schema.sql
--
-- Tables are listed in FK-dependency order (users first, then anything
-- that references it, etc.) so running this top-to-bottom just works.
-- Verified against a real throwaway database before committing this.
--
-- This is a one-time snapshot, not a migration system - if the schema
-- changes again, either update this file by hand or (recommended once
-- this app has real users) introduce Flyway/Liquibase so schema changes
-- are versioned and repeatable instead of ad-hoc.

-- Table: users
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

-- Table: chatbots
CREATE TABLE `chatbots` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  `description` text,
  `pinecone_namespace` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_chatbot_user` (`user_id`),
  CONSTRAINT `fk_chatbot_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

-- Table: documents
CREATE TABLE `documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chatbot_id` bigint NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_type` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_document_chatbot` (`chatbot_id`),
  CONSTRAINT `fk_document_chatbot` FOREIGN KEY (`chatbot_id`) REFERENCES `chatbots` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

-- Table: document_chunks
CREATE TABLE `document_chunks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_id` bigint NOT NULL,
  `chunk_index` int NOT NULL,
  `vector_id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_chunk_document` (`document_id`),
  CONSTRAINT `fk_chunk_document` FOREIGN KEY (`document_id`) REFERENCES `documents` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

-- Table: conversation_messages
CREATE TABLE `conversation_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chatbot_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role` varchar(20) NOT NULL,
  `content` text NOT NULL,
  `model` varchar(255) DEFAULT NULL,
  `total_tokens` int DEFAULT NULL,
  `latency_ms` bigint DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_conversation_message_chatbot` (`chatbot_id`),
  KEY `fk_conversation_message_user` (`user_id`),
  KEY `idx_conversation_message_created_at` (`created_at`),
  CONSTRAINT `fk_conversation_message_chatbot` FOREIGN KEY (`chatbot_id`) REFERENCES `chatbots` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_conversation_message_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
;

