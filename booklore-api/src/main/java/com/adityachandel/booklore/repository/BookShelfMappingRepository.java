package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookShelfKey;
import com.adityachandel.booklore.model.entity.BookShelfMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookShelfMappingRepository extends JpaRepository<BookShelfMapping, BookShelfKey> {
}