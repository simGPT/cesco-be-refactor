package com.practice.likelionhackathoncesco.domain.comment.repository;

import com.practice.likelionhackathoncesco.domain.comment.entity.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

  // 게시글 별 댓글 전체 조회
  List<Comment> findAllByPostPostId(Long postId);

  // 게시글 별 댓글 개수 조회
  long countByPostPostId(Long postId);

  // 게시글 목록에 따른 댓글 개수 목록 조회
  @Query(
      "SELECT c.post.postId, COUNT(c) FROM Comment c WHERE c.post.postId IN :postIds GROUP BY c.post.postId")
  List<Object[]> countByPostIdList(@Param("postIds") List<Long> postIds);
}
