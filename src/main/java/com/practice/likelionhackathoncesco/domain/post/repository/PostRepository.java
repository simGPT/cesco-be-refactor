package com.practice.likelionhackathoncesco.domain.post.repository;

import com.practice.likelionhackathoncesco.domain.post.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 도로명코드+건물본번 별 게시글 최신순 커서 페이지네이션 조회
    @Query("""
            SELECT p FROM Post p
            WHERE p.roadCode = :roadCode AND p.buildingNumber = :buildingNumber
              AND (p.createdAt < :createdAt OR (p.createdAt = :createdAt AND p.postId < :postId))
            ORDER BY p.createdAt DESC, p.postId DESC
            """)
    List<Post> findAllByLatestCursor(
            @Param("roadCode") String roadCode,
            @Param("buildingNumber") String buildingNumber,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("postId") Long postId,
            Pageable pageable);

    // 도로명코드+건물본번 별 게시글 인기순 커서 페이지네이션 조회
    @Query("""
            SELECT p FROM Post p
            WHERE p.roadCode = :roadCode AND p.buildingNumber = :buildingNumber
              AND (p.likeCount < :likeCount OR (p.likeCount = :likeCount AND p.postId < :postId))
            ORDER BY p.likeCount DESC, p.postId DESC
            """)
    List<Post> findAllByPopularCursor(
            @Param("roadCode") String roadCode,
            @Param("buildingNumber") String buildingNumber,
            @Param("likeCount") Integer likeCount,
            @Param("postId") Long postId,
            Pageable pageable);
}
