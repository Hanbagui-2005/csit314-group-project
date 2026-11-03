package com.csit314.platform.repository;

import com.csit314.platform.model.PhotoHash;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoHashRepository extends JpaRepository<PhotoHash, Long> {

    // PM-04 - every other designer's photo hashes, to compare a new upload against.
    // Small enough to compare in Java for a project this size; a real production system
    // would index these for a nearest-neighbour lookup instead of a full scan
    List<PhotoHash> findByProjectDesignerIdNot(Long designerId);

    // PM-04 - lets the backfill runner skip a photo that's already been hashed, so it's
    // safe to run on every startup instead of only once
    boolean existsByPhotoUrl(String photoUrl);
}
