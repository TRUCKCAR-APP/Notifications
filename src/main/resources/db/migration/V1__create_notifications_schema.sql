-- =============================================================================
-- V1 - Esquema inicial: tablas notification_templates, notifications y audio_cache
-- Microservicio Notifications (TruckCar)
-- Compatible con PostgreSQL y H2 PostgreSQL Mode
-- =============================================================================

CREATE TABLE notification_templates (
    id            UUID                      DEFAULT gen_random_uuid() PRIMARY KEY,
    category      VARCHAR(50)               NOT NULL,
    language      VARCHAR(10)               NOT NULL DEFAULT 'es-CO',
    channel       VARCHAR(20)               NOT NULL,
    template_text TEXT                      NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
    CONSTRAINT uk_template_cat_lang_chan UNIQUE (category, language, channel)
);

CREATE INDEX idx_templates_lookup ON notification_templates (category, language, channel);

CREATE TABLE notifications (
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

CREATE INDEX idx_notifications_recipient    ON notifications (recipient_user_id);
CREATE INDEX idx_notifications_source_event ON notifications (source_event_id, channel);
CREATE INDEX idx_notifications_status       ON notifications (status);
CREATE INDEX idx_notifications_created_at   ON notifications (created_at DESC);

CREATE TABLE audio_cache (
    id               UUID                      DEFAULT gen_random_uuid() PRIMARY KEY,
    text_hash        VARCHAR(64)               NOT NULL UNIQUE,
    s3_key           VARCHAR(255)              NOT NULL,
    voice_id         VARCHAR(50)               NOT NULL,
    language         VARCHAR(10)               NOT NULL DEFAULT 'es-CO',
    created_at       TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
    last_accessed_at TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audio_cache_hash ON audio_cache (text_hash);

-- =============================================================================
-- Semillas de plantillas por defecto en español (es-CO)
-- =============================================================================

INSERT INTO notification_templates (category, language, channel, template_text) VALUES
('REASIGNACION_CARGA', 'es-CO', 'PUSH', 'Tu carga {{loadId}} ha sido reasignada a la ruta {{ruta}}. Motivo: {{reason}}.'),
('ALERTA_RUTA', 'es-CO', 'VOICE', 'Atención conductor. Se reporta {{alertType}} en {{roadSegment}}. {{description}}. Conduzca con precaución.'),
('CIERRE_VIAL', 'es-CO', 'VOICE', 'Alerta vial. Cierre total reportado en {{location}}. Se sugiere desvío: {{detourDescription}}.'),
('MENSAJE_DESPACHADOR', 'es-CO', 'PUSH', 'Mensaje de {{dispatcherName}}: {{message}}'),
('MENSAJE_DESPACHADOR', 'es-CO', 'VOICE', 'Mensaje de su despachador {{dispatcherName}}: {{message}}'),
('CAMBIO_ESTADO_VIAJE', 'es-CO', 'PUSH', 'El estado de tu viaje {{tripId}} ha cambiado a {{status}} en {{location}}.');
