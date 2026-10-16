
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `chatop`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `chatop`;

CREATE TABLE `USERS` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(255) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `created_at` TIMESTAMP NOT NULL,
  `updated_at` TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `USERS_index` (`email`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `RENTALS` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) DEFAULT NULL,
  `surface` DECIMAL(12,2) DEFAULT NULL,
  `price` DECIMAL(12,2) DEFAULT NULL,
  `picture` VARCHAR(255) DEFAULT NULL,
  `description` VARCHAR(2000) DEFAULT NULL,
  `owner_id` INT NOT NULL,
  `created_at` TIMESTAMP NULL DEFAULT NULL,
  `updated_at` TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `owner_id` (`owner_id`),
  CONSTRAINT `RENTALS_ibfk_1`
    FOREIGN KEY (`owner_id`) REFERENCES `USERS` (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `MESSAGES` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `rental_id` INT NOT NULL,
  `user_id` INT NOT NULL,
  `message` VARCHAR(2000) NOT NULL,
  `created_at` TIMESTAMP NOT NULL,
  `updated_at` TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `rental_id` (`rental_id`),
  CONSTRAINT `MESSAGES_ibfk_1`
    FOREIGN KEY (`user_id`) REFERENCES `USERS` (`id`),
  CONSTRAINT `MESSAGES_ibfk_2`
    FOREIGN KEY (`rental_id`) REFERENCES `RENTALS` (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
