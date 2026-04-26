package org.grimmory.repository;

import org.grimmory.model.entity.MetadataFetchProposalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetadataFetchProposalRepository extends JpaRepository<MetadataFetchProposalEntity, Long> {

}
