package com.skillbook.tutor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface TutorProfileRepository extends JpaRepository<TutorProfile, UUID> {

    Optional<TutorProfile> findByUser_Id(UUID userId);

    @Query(value = """
            select distinct tp from TutorProfile tp
            join fetch tp.user u
            where (:qLike is null or lower(u.name) like :qLike
                or lower(coalesce(tp.bio, '')) like :qLike
                or exists (
                    select 1 from SkillOffered so where so.tutor.id = u.id
                        and lower(so.skill.name) like :qLike
                ))
              and (:skill is null or exists (
                    select 1 from SkillOffered so2 where so2.tutor.id = u.id
                        and lower(so2.skill.name) = lower(cast(:skill as string))
                ))
              and (:minRate is null or tp.hourlyRate >= :minRate)
              and (:maxRate is null or tp.hourlyRate <= :maxRate)
              and (:minRating is null or tp.rating >= :minRating)
            """,
            countQuery = """
            select count(distinct tp) from TutorProfile tp
            join tp.user u
            where (:qLike is null or lower(u.name) like :qLike
                or lower(coalesce(tp.bio, '')) like :qLike
                or exists (
                    select 1 from SkillOffered so where so.tutor.id = u.id
                        and lower(so.skill.name) like :qLike
                ))
              and (:skill is null or exists (
                    select 1 from SkillOffered so2 where so2.tutor.id = u.id
                        and lower(so2.skill.name) = lower(cast(:skill as string))
                ))
              and (:minRate is null or tp.hourlyRate >= :minRate)
              and (:maxRate is null or tp.hourlyRate <= :maxRate)
              and (:minRating is null or tp.rating >= :minRating)
            """)
    Page<TutorProfile> search(
            @Param("qLike") String qLike,
            @Param("skill") String skill,
            @Param("minRate") BigDecimal minRate,
            @Param("maxRate") BigDecimal maxRate,
            @Param("minRating") BigDecimal minRating,
            Pageable pageable);
}