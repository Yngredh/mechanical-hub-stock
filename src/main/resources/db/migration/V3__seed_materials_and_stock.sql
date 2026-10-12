-- Seed conforme design/seed_catalog.md (secoes 2 e 3), com os mesmos UUIDs.

INSERT INTO materials (id, name, description, min_stock_quantity) VALUES
('789492e9-c859-4ad7-8a9a-39d3ffb12519', 'Óleo de Motor 5W-30 Sintético (1L)', 'Óleo lubrificante sintético para motores flex e a gasolina, viscosidade 5W-30.', 20),
('eb694988-23ef-413b-b68a-c510f6346534', 'Filtro de Óleo Universal', 'Filtro de óleo rosqueável compatível com motores 1.0 a 2.0 de múltiplas marcas.', 10),
('99370d9f-1cbc-468d-8043-94588108b7c2', 'Pastilha de Freio Dianteira', 'Jogo de pastilhas de freio dianteiras com indicador de desgaste, compatível com veículos populares.', 8),
('a7f744e8-2a29-41ba-85f4-6eb13317af07', 'Fluido de Freio DOT 4 (500ml)', 'Fluido de freio sintético DOT 4, alta temperatura de ebulição, para sistemas ABS e convencionais.', 10),
('88221fb1-880c-4eb8-b0ed-be3e59d34269', 'Correia Dentada', 'Correia dentada para motores 1.0 a 1.6, fabricada em borracha reforçada com fibra de aramida.', 5),
('18c8a5ca-1d69-4690-a9c5-0877d928b96a', 'Tensor de Correia Dentada', 'Tensor automático para correia dentada, garante tensão adequada e reduz desgaste prematuro.', 5),
('b6313df9-9c00-4750-84ad-1af4912f9373', 'Rolamento Esticador de Correia', 'Rolamento esticador para kit de correia dentada, compatível com motores de 1.0 a 2.0.', 5),
('eeff1111-1111-1111-1111-000000000001', 'Vela de Ignição', 'Vela de ignição para motores 1.0 a 1.6 flex, resistência iridium para melhor desempenho.', 10),
('eeff1111-1111-1111-1111-000000000002', 'Cabo de Vela de Ignição', 'Jogo de cabos de vela com blindagem eletromagnética, compatível com motores 1.0 a 2.0.', 5),
('eeff1111-1111-1111-1111-000000000003', 'Bobina de Ignição', 'Bobina de ignição individual para motores com sistema de ignição direta (DIS).', 3);

INSERT INTO stock (material_id, status, quantity) VALUES
('789492e9-c859-4ad7-8a9a-39d3ffb12519', 'AVAILABLE', 5),
('eb694988-23ef-413b-b68a-c510f6346534', 'AVAILABLE', 5),
('99370d9f-1cbc-468d-8043-94588108b7c2', 'AVAILABLE', 0),
('a7f744e8-2a29-41ba-85f4-6eb13317af07', 'AVAILABLE', 0),
('88221fb1-880c-4eb8-b0ed-be3e59d34269', 'AVAILABLE', 3),
('18c8a5ca-1d69-4690-a9c5-0877d928b96a', 'AVAILABLE', 1),
('b6313df9-9c00-4750-84ad-1af4912f9373', 'AVAILABLE', 5),
('eeff1111-1111-1111-1111-000000000001', 'AVAILABLE', 5),
('eeff1111-1111-1111-1111-000000000002', 'AVAILABLE', 3),
('eeff1111-1111-1111-1111-000000000003', 'AVAILABLE', 2);

INSERT INTO stock (material_id, status, quantity)
SELECT id, 'RESERVED', 0 FROM materials;

INSERT INTO service_materials (service_id, material_id, quantity) VALUES
('03d94934-6201-449b-b8db-490546c35d34', '789492e9-c859-4ad7-8a9a-39d3ffb12519', 4),
('03d94934-6201-449b-b8db-490546c35d34', 'eb694988-23ef-413b-b68a-c510f6346534', 1),
('b9090e29-c8f8-498f-b536-bb70cb69113e', '99370d9f-1cbc-468d-8043-94588108b7c2', 1),
('b9090e29-c8f8-498f-b536-bb70cb69113e', 'a7f744e8-2a29-41ba-85f4-6eb13317af07', 1),
('192c4db5-61b3-4cf7-b02b-54e948a9ce5d', '88221fb1-880c-4eb8-b0ed-be3e59d34269', 1),
('192c4db5-61b3-4cf7-b02b-54e948a9ce5d', '18c8a5ca-1d69-4690-a9c5-0877d928b96a', 1),
('192c4db5-61b3-4cf7-b02b-54e948a9ce5d', 'b6313df9-9c00-4750-84ad-1af4912f9373', 1),
('eeff2222-2222-2222-2222-000000000001', 'eeff1111-1111-1111-1111-000000000001', 1),
('eeff2222-2222-2222-2222-000000000001', 'eeff1111-1111-1111-1111-000000000002', 1),
('eeff2222-2222-2222-2222-000000000001', 'eeff1111-1111-1111-1111-000000000003', 1);
