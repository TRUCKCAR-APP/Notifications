package com.truckcar.notifications.model;

public enum NotificationCategory {
    REASIGNACION_CARGA,
    ALERTA_RUTA,
    CIERRE_VIAL,
    MENSAJE_DESPACHADOR,
    CAMBIO_ESTADO_VIAJE,
    /** Botón SOS de un conductor: push inmediato a la torre de control (suscriptores del tópico SNS). */
    EMERGENCIA_SOS
}
