package com.chutiji.pool;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 池题取用流水：谁在什么时候拿到了哪道池题。
 *
 * <p>池题本身是公共资产（t_question.user_id 留空，一道题可服务多个用户），
 * 所以「谁拿过」只能记在这里，不能记在题上。</p>
 */
@Entity
@Table(name = "t_pool_pick")
public class PoolPickEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

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
