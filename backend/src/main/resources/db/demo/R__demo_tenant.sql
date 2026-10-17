-- ─────────────────────────────────────────────────────────────────────────
-- DEMO DATA: "The Barber News". Only loaded with the `demo` Spring profile
-- (application-demo.yml adds this location to Flyway). Repeatable + idempotent:
-- fixed ids and ON CONFLICT DO NOTHING, so it never duplicates or overwrites.
-- The admin user is NOT here: it is created at startup from environment
-- variables (APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD), see auth.AdminBootstrap.
-- Barbers are fictional demo people.
-- ─────────────────────────────────────────────────────────────────────────

INSERT INTO tenant (id, name, slug, domain, timezone, default_locale, currency, theme)
VALUES ('7b3f1c2e-0000-4000-8000-000000000001', 'The Barber News', 'the-barber-news', NULL,
        'Europe/Berlin', 'de', 'EUR', '{"gold": "#c9a45c", "background": "#0a0a0a"}')
ON CONFLICT (id) DO NOTHING;

-- Services (same copy and prices as the website)
INSERT INTO service (id, tenant_id, name_de, name_en, description_de, description_en,
                     duration_min, price_cents, price_from, sort_order)
VALUES ('7b3f1c2e-0000-4000-8000-000000000101', '7b3f1c2e-0000-4000-8000-000000000001',
        'Haarschnitt', 'Haircut',
        'Beratung, Präzisionsschnitt und Finish. Fades, Scherenschnitte, Klassiker.',
        'Consultation, precision cut and finish. Fades, scissor cuts, classics.',
        45, 3500, true, 1),
       ('7b3f1c2e-0000-4000-8000-000000000102', '7b3f1c2e-0000-4000-8000-000000000001',
        'Bart-Styling', 'Beard Sculpting',
        'Konturen, Line-up und heißes Tuch – mit Rasiermesser-Finish.',
        'Shape, line-up and hot towel treatment with straight-razor finish.',
        30, 2500, true, 2),
       ('7b3f1c2e-0000-4000-8000-000000000103', '7b3f1c2e-0000-4000-8000-000000000001',
        'Premium-Paket', 'Premium Styling',
        'Schnitt, Bart, Waschen und Styling. Das komplette Grooming-Ritual.',
        'Cut, beard, wash and styling. The complete grooming ritual.',
        90, 6000, true, 3)
ON CONFLICT (id) DO NOTHING;

-- Barbers
INSERT INTO staff (id, tenant_id, name, photo_url, bio_de, bio_en, sort_order)
VALUES ('7b3f1c2e-0000-4000-8000-000000000201', '7b3f1c2e-0000-4000-8000-000000000001',
        'Luca', NULL,
        'Spezialist für Fades und klassische Herrenschnitte. Seit zehn Jahren an der Schere. (Demo)',
        'Specialist in fades and classic gentlemen''s cuts. Ten years behind the chair. (Demo)', 1),
       ('7b3f1c2e-0000-4000-8000-000000000202', '7b3f1c2e-0000-4000-8000-000000000001',
        'Mo', NULL,
        'Bart-Experte mit Rasiermesser-Präzision und einem Auge fürs Detail. (Demo)',
        'Beard expert with straight-razor precision and an eye for detail. (Demo)', 2)
ON CONFLICT (id) DO NOTHING;

-- Luca offers everything; Mo offers haircut + beard (not the premium package)
INSERT INTO staff_service (tenant_id, staff_id, service_id)
VALUES ('7b3f1c2e-0000-4000-8000-000000000001', '7b3f1c2e-0000-4000-8000-000000000201', '7b3f1c2e-0000-4000-8000-000000000101'),
       ('7b3f1c2e-0000-4000-8000-000000000001', '7b3f1c2e-0000-4000-8000-000000000201', '7b3f1c2e-0000-4000-8000-000000000102'),
       ('7b3f1c2e-0000-4000-8000-000000000001', '7b3f1c2e-0000-4000-8000-000000000201', '7b3f1c2e-0000-4000-8000-000000000103'),
       ('7b3f1c2e-0000-4000-8000-000000000001', '7b3f1c2e-0000-4000-8000-000000000202', '7b3f1c2e-0000-4000-8000-000000000101'),
       ('7b3f1c2e-0000-4000-8000-000000000001', '7b3f1c2e-0000-4000-8000-000000000202', '7b3f1c2e-0000-4000-8000-000000000102')
ON CONFLICT DO NOTHING;

-- Opening hours Mon–Sat (website: Mon–Fri 09–20, Sat 09–18, Sun closed)
-- Luca: Mon–Fri 09:00–13:00 + 14:00–20:00 (lunch break), Sat 09:00–18:00
INSERT INTO working_hours (id, tenant_id, staff_id, weekday, start_time, end_time)
SELECT ('7b3f1c2e-0000-4000-8001-0000000001' || lpad(d::text, 2, '0'))::uuid,
       '7b3f1c2e-0000-4000-8000-000000000001', '7b3f1c2e-0000-4000-8000-000000000201', d, '09:00', '13:00'
FROM generate_series(1, 5) AS d
ON CONFLICT (id) DO NOTHING;

INSERT INTO working_hours (id, tenant_id, staff_id, weekday, start_time, end_time)
SELECT ('7b3f1c2e-0000-4000-8001-0000000002' || lpad(d::text, 2, '0'))::uuid,
       '7b3f1c2e-0000-4000-8000-000000000001', '7b3f1c2e-0000-4000-8000-000000000201', d, '14:00', '20:00'
FROM generate_series(1, 5) AS d
ON CONFLICT (id) DO NOTHING;

INSERT INTO working_hours (id, tenant_id, staff_id, weekday, start_time, end_time)
VALUES ('7b3f1c2e-0000-4000-8001-000000000306', '7b3f1c2e-0000-4000-8000-000000000001',
        '7b3f1c2e-0000-4000-8000-000000000201', 6, '09:00', '18:00')
ON CONFLICT (id) DO NOTHING;

-- Mo: Mon–Fri 10:00–19:00, Sat 09:00–18:00
INSERT INTO working_hours (id, tenant_id, staff_id, weekday, start_time, end_time)
SELECT ('7b3f1c2e-0000-4000-8001-0000000004' || lpad(d::text, 2, '0'))::uuid,
       '7b3f1c2e-0000-4000-8000-000000000001', '7b3f1c2e-0000-4000-8000-000000000202', d, '10:00', '19:00'
FROM generate_series(1, 5) AS d
ON CONFLICT (id) DO NOTHING;

INSERT INTO working_hours (id, tenant_id, staff_id, weekday, start_time, end_time)
VALUES ('7b3f1c2e-0000-4000-8001-000000000506', '7b3f1c2e-0000-4000-8000-000000000001',
        '7b3f1c2e-0000-4000-8000-000000000202', 6, '09:00', '18:00')
ON CONFLICT (id) DO NOTHING;
