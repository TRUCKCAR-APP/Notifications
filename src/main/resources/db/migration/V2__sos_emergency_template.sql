-- Categoría EMERGENCIA_SOS (botón SOS del conductor) y su plantilla push. Idempotente.
--
-- Los esquemas creados antes de Flyway (baseline) traen CHECK de categoría generados por Hibernate con la lista de
-- entonces: se reemplazan por la lista completa en las dos tablas. En un esquema creado desde V1 no existían.
ALTER TABLE notification_templates DROP CONSTRAINT IF EXISTS notification_templates_category_check;
ALTER TABLE notification_templates ADD CONSTRAINT notification_templates_category_check CHECK (category IN (
    'REASIGNACION_CARGA', 'ALERTA_RUTA', 'CIERRE_VIAL', 'MENSAJE_DESPACHADOR', 'CAMBIO_ESTADO_VIAJE', 'EMERGENCIA_SOS'));

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_category_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_category_check CHECK (category IN (
    'REASIGNACION_CARGA', 'ALERTA_RUTA', 'CIERRE_VIAL', 'MENSAJE_DESPACHADOR', 'CAMBIO_ESTADO_VIAJE', 'EMERGENCIA_SOS'));

INSERT INTO notification_templates (id, category, language, channel, template_text, created_at, updated_at)
SELECT gen_random_uuid(), 'EMERGENCIA_SOS', 'es-CO', 'PUSH',
       'SOS: {{driverName}} ({{vehiclePlate}}) pide ayuda en {{latitude}}, {{longitude}}. Carga {{loadId}}. Mapa: {{mapsUrl}}',
       now(), now()
ON CONFLICT (category, language, channel) DO NOTHING;
