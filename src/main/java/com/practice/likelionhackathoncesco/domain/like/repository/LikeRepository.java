package com.practice.likelionhackathoncesco.domain.like.repository;

import com.practice.likelionhackathoncesco.domain.like.entity.Like;
import com.practice.likelionhackathoncesco.domain.post.entity.Post;
import com.practice.likelionhackathoncesco.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<Like, Long> {

  // (사용자 + 게시글) 별 좋아요 조회
  Optional<Like> findByUserAndPost(User user, Post post);

  // 사용자, 게시글 별 좋아요 존재 여부 확인 메소드
  Boolean existsByUserUserIdAndPost(Long userId, Post post);

  // 사용자와 게시글별 좋아요 반환 메서드
  List<Like> findAllByUserUserIdAndPostPostIdIn(Long userId, List<Long> postIdList);
}
