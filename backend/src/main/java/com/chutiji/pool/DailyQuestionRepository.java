package com.chutiji.pool;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyQuestionRepository extends JpaRepository<DailyQuestionEntity, Long> {

    /** (user_id, daily_date) 上有唯一键，所以最多一行。 */
    Optional<DailyQuestionEntity> findByUserIdAndDailyDate(Long userId, LocalDate dailyDate);
}
