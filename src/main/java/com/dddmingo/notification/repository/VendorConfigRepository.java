package com.dddmingo.notification.repository;

import com.dddmingo.notification.model.entity.VendorConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VendorConfigRepository extends JpaRepository<VendorConfig, String> {

    List<VendorConfig> findByEnabledTrue();
}
