package com.rewatch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rewatch.model.EmailDeliveryRecord;

public interface EmailDeliveryRecordRepository extends JpaRepository<EmailDeliveryRecord, Long> {
    List<EmailDeliveryRecord> findByStatus(EmailDeliveryRecord.Status status);
}
