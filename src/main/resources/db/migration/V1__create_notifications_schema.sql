-- =============================================================================
-- V1 - Esquema inicial: tablas notification_templates, notifications y audio_cache
-- Microservicio Notifications (TruckCar)
-- Compatible con PostgreSQL y H2 PostgreSQL Mode
-- =============================================================================

CREATE TABLE IF NOT EXISTS notification_templates (
    id            UUID                      DEFAULT gen_random_uuid() PRIMARY KEY,
    category      VARCHAR(50)               NOT NULL,
    language      VARCHAR(10)               NOT NULL DEFAULT 'es-CO',
    channel       VARCHAR(20)               NOT NULL,
    template_text TEXT                      NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
    CONSTRAINT uk_template_cat_lang_chan UNIQUE (category, language, channel)
);

CREATE INDEX IF NOT EXISTS idx_templates_lookup ON notification_templates (category, language, channel);

CREATE TABLE IF NOT EXISTS notifications (
    id                UUID                      DEFAULT gen_random_uuid() PRIMARY KEY,
    recipient_user_id UUID                      NOT NULL,
    channel           VARCHAR(20)               NOT NULL,
    category          VARCHAR(50)               NOT NULL,
    source_event_id   VARCHAR(100)              NOT NULL,
    source_event_type VARCHAR(100)              NOT NULL,
    payload           TEXT                      NOT NULL,
    audio_url         TEXT,
    status            VARCHAR(20)               NOT NULL DEFAULT 'PENDING',
    failure_reason    TEXT,
    attempts          INT                       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
    sent_at           TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient    ON notifications (recipient_user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_source_event ON notifications (source_event_id, channel);
CREATE INDEX IF NOT EXISTS idx_notifications_status       ON notifications (status);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at   ON notifications (created_at DESC);

CREATE TABLE IF NOT EXISTS audio_cache (
    id               UUID                      DEFAULT gen_random_uuid() PRIMARY KEY,
    text_hash        VARCHAR(64)               NOT NULL UNIQUE,
    s3_key           VARCHAR(255)              NOT NULL,
    voice_id         VARCHAR(50)               NOT NULL,
    language         VARCHAR(10)               NOT NULL DEFAULT 'es-CO',
    created_at       TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
    last_accessed_at TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audio_cache_hash ON audio_cache (text_hash);

-- =============================================================================
-- Semillas de plantillas por defecto en español (es-CO)
-- =============================================================================

-- id, created_at y updated_at explícitos: en bases existentes la tabla la creó Hibernate sin valores por defecto.
-- ON CONFLICT: idempotente si las plantillas ya existen.
INSERT INTO notification_templates (id, category, language, channel, template_text, created_at, updated_at)
SELECT gen_random_uuid(), seed.category, seed.language, seed.channel, seed.template_text, now(), now()
FROM (VALUES
('REASIGNACION_CARGA', 'es-CO', 'PUSH', 'Tu carga {{loadId}} ha sido reasignada a la ruta {{ruta}}. Motivo: {{reason}}.'),
('ALERTA_RUTA', 'es-CO', 'VOICE', 'Atención conductor. Se reporta {{alertType}} en {{roadSegment}}. {{description}}. Conduzca con precaución.'),
('CIERRE_VIAL', 'es-CO', 'VOICE', 'Alerta vial. Cierre total reportado en {{location}}. Se sugiere desvío: {{detourDescription}}.'),
('MENSAJE_DESPACHADOR', 'es-CO', 'PUSH', 'Mensaje de {{dispatcherName}}: {{message}}'),
('MENSAJE_DESPACHADOR', 'es-CO', 'VOICE', 'Mensaje de su despachador {{dispatcherName}}: {{message}}'),
('CAMBIO_ESTADO_VIAJE', 'es-CO', 'PUSH', 'El estado de tu viaje {{tripId}} ha cambiado a {{status}} en {{location}}.')
) AS seed (category, language, channel, template_text)
ON CONFLICT (category, language, channel) DO NOTHING;
