package com.dddmingo.notification.repository;

import com.dddmingo.notification.model.entity.DeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, Long> {

    List<DeliveryLog> findByNotificationIdOrderByAttemptAsc(String notificationId);
}
