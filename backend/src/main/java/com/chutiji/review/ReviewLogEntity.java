package com.chutiji.review;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 每次评分的 S/D 快照。现在不训练参数，但这是日后训练个性化 FSRS 的唯一数据来源。
 */
@Entity
@Table(name = "t_review_log")
public class ReviewLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    /** 1=Again 2=Hard 3=Good 4=Easy。DDL 是 TINYINT，columnDefinition 必须对齐，否则 ddl-auto=validate 会报 integer vs tinyint。 */
    @Column(name = "rating", nullable = false, columnDefinition = "tinyint")
    private Integer rating;

    @Column(name = "s_before", nullable = false)
    private Double sBefore;

    @Column(name = "d_before", nullable = false)
    private Double dBefore;

    @Column(name = "s_after", nullable = false)
    private Double sAfter;

    @Column(name = "d_after", nullable = false)
    private Double dAfter;

    @Column(name = "interval_days", nullable = false)
    private Double intervalDays;

    @Column(name = "reviewed_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime reviewedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Double getSBefore() {
        return sBefore;
    }

    public void setSBefore(Double sBefore) {
        this.sBefore = sBefore;
    }

    public Double getDBefore() {
        return dBefore;
    }

    public void setDBefore(Double dBefore) {
        this.dBefore = dBefore;
    }

    public Double getSAfter() {
        return sAfter;
    }

    public void setSAfter(Double sAfter) {
        this.sAfter = sAfter;
    }

    public Double getDAfter() {
        return dAfter;
    }

    public void setDAfter(Double dAfter) {
        this.dAfter = dAfter;
    }

    public Double getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(Double intervalDays) {
        this.intervalDays = intervalDays;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }
}
