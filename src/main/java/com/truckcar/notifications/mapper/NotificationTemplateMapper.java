package com.truckcar.notifications.mapper;

import com.truckcar.notifications.dto.response.NotificationTemplateResponse;
import com.truckcar.notifications.model.NotificationTemplate;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationTemplateMapper {

    NotificationTemplateResponse toResponse(NotificationTemplate template);

    List<NotificationTemplateResponse> toResponseList(List<NotificationTemplate> templates);
}
