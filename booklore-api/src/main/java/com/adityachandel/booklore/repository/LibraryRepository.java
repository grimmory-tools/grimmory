package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.LibraryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LibraryRepository extends JpaRepository<LibraryEntity, Long>, JpaSpecificationExecutor<LibraryEntity> {

    List<LibraryEntity> findByIdIn(List<Long> ids);
}
