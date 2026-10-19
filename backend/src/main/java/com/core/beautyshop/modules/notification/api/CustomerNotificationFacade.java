package com.core.beautyshop.modules.notification.api;

public interface CustomerNotificationFacade {
    void publish(Long userId,String eventKey,String type,String title,String body,String target);
}
