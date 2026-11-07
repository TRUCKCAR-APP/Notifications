-- La plantilla push de REASIGNACION_CARGA decía "ha sido reasignada" también para la primera propuesta, y dejaba
-- huecos cuando el evento no traía {{ruta}} ni {{reason}}. Ahora el consumidor siempre arma la ruta y el motivo, y el
-- texto sirve para propuesta, asignación y reasignación:
--   "Carga 12 (Bogotá → Tunja): se la propone el motor de asignación. Tiene 5 minutos para aceptarla o rechazarla en la app."
-- Solo se reemplaza si sigue siendo el texto original de V1: una plantilla editada por un administrador no se toca.
UPDATE notification_templates
SET template_text = 'Carga {{loadId}} ({{ruta}}): {{reason}}.', updated_at = now()
WHERE category = 'REASIGNACION_CARGA' AND language = 'es-CO' AND channel = 'PUSH'
  AND template_text = 'Tu carga {{loadId}} ha sido reasignada a la ruta {{ruta}}. Motivo: {{reason}}.';
