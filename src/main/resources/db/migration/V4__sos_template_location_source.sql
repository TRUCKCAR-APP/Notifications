-- El SOS puede llegar sin GPS (Community-Alerts: locationSource DEVICE_GPS, LAST_KNOWN o UNKNOWN). La plantilla usa
-- {{location}}, que ya dice si es la posición del dispositivo, la última conocida o ninguna. Solo se actualiza si
-- sigue con el texto original (no pisa una plantilla personalizada).
UPDATE notification_templates
SET template_text = 'SOS: {{driverName}} ({{vehiclePlate}}) pide ayuda {{location}}. Carga {{loadId}}. Mapa: {{mapsUrl}}',
    updated_at = now()
WHERE category = 'EMERGENCIA_SOS' AND language = 'es-CO' AND channel = 'PUSH'
  AND template_text = 'SOS: {{driverName}} ({{vehiclePlate}}) pide ayuda en {{latitude}}, {{longitude}}. Carga {{loadId}}. Mapa: {{mapsUrl}}';
