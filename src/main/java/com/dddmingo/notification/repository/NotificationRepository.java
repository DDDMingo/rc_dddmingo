package com.dddmingo.notification.repository;

import com.dddmingo.notification.model.entity.Notification;
import com.dddmingo.notification.model.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    Optional<Notification> findByBizId(String bizId);

    /**
     * CAS 抢占：将状态从 pending/retrying 更新为 sending，保证多实例下只有一个实例能抢到
     */
    @Modifying
    @Query("UPDATE Notification n SET n.status = 'SENDING', n.updatedAt = :now " +
           "WHERE n.id = :id AND n.status = :expectedStatus")
    int casUpdateStatus(@Param("id") String id,
                        @Param("expectedStatus") NotificationStatus expectedStatus,
                        @Param("now") OffsetDateTime now);

    /**
     * 扫描待投递/待重试的通知（轮询兜底用）
     */
    @Query("SELECT n FROM Notification n WHERE n.status IN :statuses " +
           "AND n.nextRetryAt <= :now ORDER BY n.nextRetryAt ASC")
    List<Notification> findPendingNotifications(@Param("statuses") List<NotificationStatus> statuses,
                                                 @Param("now") OffsetDateTime now);

    /**
     * 按供应商查询通知（管理查询用）
     */
    List<Notification> findByVendorCodeAndStatusIn(String vendorCode, List<NotificationStatus> statuses);
}
