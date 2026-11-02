package com.chutiji.pool;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 每日一题的落库记录：一个用户在一个自然日只有一行。 */
@Entity
@Table(name = "t_daily_question")
public class DailyQuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "daily_date", nullable = false)
    private LocalDate dailyDate;

    @Column(name = "question_id", length = 32, nullable = false)
    private String questionId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDate getDailyDate() {
        return dailyDate;
    }

    public void setDailyDate(LocalDate dailyDate) {
        this.dailyDate = dailyDate;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
