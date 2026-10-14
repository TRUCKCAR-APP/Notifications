package com.truckcar.notifications.mapper;

import com.truckcar.notifications.dto.response.NotificationResponse;
import com.truckcar.notifications.model.Notification;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);

    List<NotificationResponse> toResponseList(List<Notification> notifications);
}
