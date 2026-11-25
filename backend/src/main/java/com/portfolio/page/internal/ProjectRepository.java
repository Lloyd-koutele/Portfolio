package com.portfolio.page.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ProjectRepository extends JpaRepository<ProjectEntity, UUID>
{
    List<ProjectEntity> findAllByOrderByPositionAsc();

    @Query("select coalesce(max(p.position), -1) from ProjectEntity p")
    int maxPosition();
}
