package com.foliopro.api.repository;

import com.foliopro.api.model.PortfolioEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Queries shared by the USA and India entry tables; each table has its own repository. */
@NoRepositoryBean
public interface PortfolioEntryRepository<T extends PortfolioEntryEntity> extends JpaRepository<T, Long> {
    List<T> findByUserIdOrderByEntryMonthAsc(Long userId);

    List<T> findByUserIdAndEntryMonth(Long userId, LocalDate entryMonth);

    Optional<T> findFirstByUserIdAndEntryMonthBeforeOrderByEntryMonthDesc(Long userId, LocalDate entryMonth);
}
