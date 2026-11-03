package com.bolpay.repository;

import com.bolpay.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Vendor entity
 */
@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByPhoneNumber(String phoneNumber);

    Optional<Vendor> findBySlug(String slug);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsBySlug(String slug);
}
