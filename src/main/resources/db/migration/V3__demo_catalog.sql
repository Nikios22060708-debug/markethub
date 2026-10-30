-- Демонстрационный каталог, чтобы API сразу возвращало данные.
INSERT INTO categories (name, description) VALUES ('Ноутбуки', 'Портативные компьютеры для работы и учёбы');
INSERT INTO categories (name, description) VALUES ('Смартфоны', 'Телефоны на Android и iOS');
INSERT INTO categories (name, description) VALUES ('Аксессуары', 'Наушники, зарядки, чехлы');

INSERT INTO products (name, description, price, stock, category_id)
SELECT 'Ultrabook Air 14', 'Лёгкий ноутбук, 16 ГБ ОЗУ, SSD 512 ГБ', 89990.00, 12, id FROM categories WHERE name = 'Ноутбуки';
INSERT INTO products (name, description, price, stock, category_id)
SELECT 'ProBook 16', 'Производительный ноутбук для разработки, 32 ГБ ОЗУ', 149990.00, 5, id FROM categories WHERE name = 'Ноутбуки';
INSERT INTO products (name, description, price, stock, category_id)
SELECT 'Phone X Lite', 'Смартфон 6.1", 128 ГБ', 34990.00, 30, id FROM categories WHERE name = 'Смартфоны';
INSERT INTO products (name, description, price, stock, category_id)
SELECT 'Phone X Max', 'Флагманский смартфон 6.7", 512 ГБ', 119990.00, 8, id FROM categories WHERE name = 'Смартфоны';
INSERT INTO products (name, description, price, stock, category_id)
SELECT 'Наушники BassPods', 'Беспроводные наушники с шумоподавлением', 12990.00, 50, id FROM categories WHERE name = 'Аксессуары';
INSERT INTO products (name, description, price, stock, category_id)
SELECT 'Зарядка 65W GaN', 'Компактное зарядное устройство USB-C', 3490.00, 100, id FROM categories WHERE name = 'Аксессуары';
