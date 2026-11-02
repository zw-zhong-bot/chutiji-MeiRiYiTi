package com.chutiji.review;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 复习卡：粒度 = 模块 + 难度配置，不是某一道具体的题。 */
@Entity
@Table(name = "t_review_card")
public class ReviewCardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "module_key", length = 32, nullable = false)
    private String moduleKey;

    /** JSON 列：该知识点对应的难度配置，复习时原样交给模块出题。 */
    @Column(name = "difficulty", nullable = false, columnDefinition = "json")
    private String difficulty;

    @Column(name = "knowledge_key", length = 128, nullable = false)
    private String knowledgeKey;

    @Column(name = "stability", nullable = false)
    private Double stability;

    @Column(name = "difficulty_fsrs", nullable = false)
    private Double difficultyFsrs;

    @Column(name = "reps", nullable = false)
    private Integer reps;

    @Column(name = "lapses", nullable = false)
    private Integer lapses;

    @Column(name = "state", length = 16, nullable = false)
    private String state;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "last_review_at")
    private LocalDateTime lastReviewAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(String moduleKey) {
        this.moduleKey = moduleKey;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getKnowledgeKey() {
        return knowledgeKey;
    }

    public void setKnowledgeKey(String knowledgeKey) {
        this.knowledgeKey = knowledgeKey;
    }

    public Double getStability() {
        return stability;
    }

    public void setStability(Double stability) {
        this.stability = stability;
    }

    public Double getDifficultyFsrs() {
        return difficultyFsrs;
    }

    public void setDifficultyFsrs(Double difficultyFsrs) {
        this.difficultyFsrs = difficultyFsrs;
    }

    public Integer getReps() {
        return reps;
    }

    public void setReps(Integer reps) {
        this.reps = reps;
    }

    public Integer getLapses() {
        return lapses;
    }

    public void setLapses(Integer lapses) {
        this.lapses = lapses;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public LocalDateTime getLastReviewAt() {
        return lastReviewAt;
    }

    public void setLastReviewAt(LocalDateTime lastReviewAt) {
        this.lastReviewAt = lastReviewAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
