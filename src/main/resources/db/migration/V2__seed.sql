
INSERT INTO categories (id, name) VALUES
    (gen_random_uuid(), 'Hortifrúti'),
    (gen_random_uuid(), 'Açougue'),
    (gen_random_uuid(), 'Padaria'),
    (gen_random_uuid(), 'Mercearia'),
    (gen_random_uuid(), 'Limpeza');


INSERT INTO products (id, category_id, name, description, price, stock, is_active)
SELECT gen_random_uuid(), c.id, p.name, p.description, p.price, p.stock, p.is_active
FROM (VALUES
        ('Hortifrúti', 'Banana prata (kg)',     'Banana prata fresca',                     6.99, 120, TRUE),
        ('Hortifrúti', 'Maçã gala (kg)',        'Maçã gala nacional selecionada',          9.90,  90, TRUE),
        ('Hortifrúti', 'Tomate italiano (kg)',  'Tomate italiano para molhos e saladas',   8.49,  80, TRUE),
        ('Hortifrúti', 'Alface crespa (un)',    'Alface crespa fresca, maço',              3.49,  60, TRUE),
        ('Hortifrúti', 'Batata inglesa (kg)',   'Batata inglesa lavada',                   5.99, 150, TRUE),
        ('Hortifrúti', 'Cenoura (kg)',          'Cenoura fresca',                          4.79,   3, TRUE),   -- estoque baixo

        ('Açougue',    'Patinho bovino (kg)',   'Corte magro para moer ou grelhar',       39.90,  40, TRUE),
        ('Açougue',    'Peito de frango (kg)',  'Peito de frango sem osso, resfriado',    18.90,  70, TRUE),
        ('Açougue',    'Linguiça toscana (kg)', 'Linguiça suína tipo toscana',            22.50,  50, TRUE),
        ('Açougue',    'Costela bovina (kg)',   'Costela bovina em tiras',                32.90,   0, TRUE),  -- sem estoque
        ('Açougue',    'Filé de tilápia (kg)',  'Filé de tilápia congelado',              34.90,  25, TRUE),

        ('Padaria',    'Pão francês (kg)',      'Pão francês fresco do dia',              17.90,  60, TRUE),
        ('Padaria',    'Pão de forma integral', 'Pão de forma integral 500g',              9.49,  45, TRUE),
        ('Padaria',    'Bolo de cenoura',       'Bolo de cenoura com cobertura de chocolate', 24.90, 10, TRUE),
        ('Padaria',    'Rosca doce',            'Rosca doce com coco',                    12.90,  20, TRUE),
        ('Padaria',    'Croissant de manteiga', 'Croissant artesanal de manteiga',         7.50,  30, TRUE),

        ('Mercearia',  'Arroz branco 5kg',      'Arroz branco tipo 1',                    28.90, 100, TRUE),
        ('Mercearia',  'Feijão carioca 1kg',    'Feijão carioca tipo 1',                   8.99, 110, TRUE),
        ('Mercearia',  'Leite integral 1L',     'Leite UHT integral',                      5.29, 200, TRUE),
        ('Mercearia',  'Leite desnatado 1L',    'Leite UHT desnatado',                     5.49, 150, TRUE),
        ('Mercearia',  'Café torrado 500g',     'Café torrado e moído, tradicional',      19.90,  75, TRUE),
        ('Mercearia',  'Macarrão espaguete 500g','Macarrão espaguete',           4.99, 130, TRUE),

        ('Limpeza',    'Detergente 500ml',      'Detergente líquido neutro',               2.79, 200, TRUE),
        ('Limpeza',    'Sabão em pó 1kg',       'Sabão em pó para roupas',                14.90,  60, TRUE),
        ('Limpeza',    'Água sanitária 2L',     'Água sanitária com cloro ativo',          6.49,  90, TRUE),
        ('Limpeza',    'Desinfetante 1L',       'Desinfetante perfumado lavanda',          5.99,  80, TRUE),
        ('Limpeza',    'Esponja multiuso (3un)','Esponja dupla face',                      4.29, 140, TRUE)
     ) AS p(category_name, name, description, price, stock, is_active)
         JOIN categories c ON c.name = p.category_name;