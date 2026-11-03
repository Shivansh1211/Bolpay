package com.bolpay.repository;

import com.bolpay.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

/**
 * Repository for Vendor entity
 */
@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByPhoneNumber(String phoneNumber);

    Optional<Vendor> findBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vendor v where v.slug = :slug")
    Optional<Vendor> findBySlugForUpdate(@Param("slug") String slug);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsBySlug(String slug);
}
