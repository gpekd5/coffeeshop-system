-- 직접 설치한 MySQL을 사용하는 경우 root 권한으로 한 번 실행한다.
-- Docker Compose의 mysql 서비스는 같은 DB와 계정을 자동 생성하므로 보통 실행할 필요가 없다.

CREATE DATABASE IF NOT EXISTS coffee_order
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'coffee'@'%' IDENTIFIED BY 'coffee';
CREATE USER IF NOT EXISTS 'coffee'@'localhost' IDENTIFIED BY 'coffee';
GRANT ALL PRIVILEGES ON coffee_order.* TO 'coffee'@'%';
GRANT ALL PRIVILEGES ON coffee_order.* TO 'coffee'@'localhost';
FLUSH PRIVILEGES;
