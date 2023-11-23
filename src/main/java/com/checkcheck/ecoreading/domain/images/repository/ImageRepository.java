package com.checkcheck.ecoreading.domain.images.repository;

import com.checkcheck.ecoreading.domain.images.entity.Images;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageRepository extends JpaRepository<Images, Long> {
    Images findByImagesId(Long imageId);
}
