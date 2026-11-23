package com.practice.likelionhackathoncesco.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.practice.likelionhackathoncesco.domain.comment.entity.Comment;
import com.practice.likelionhackathoncesco.domain.comment.repository.CommentRepository;
import com.practice.likelionhackathoncesco.domain.like.entity.Like;
import com.practice.likelionhackathoncesco.domain.like.repository.LikeRepository;
import com.practice.likelionhackathoncesco.domain.post.entity.Post;
import com.practice.likelionhackathoncesco.domain.post.mapper.PostMapper;
import com.practice.likelionhackathoncesco.domain.post.repository.PostRepository;
import com.practice.likelionhackathoncesco.domain.user.entity.PayStatus;
import com.practice.likelionhackathoncesco.domain.user.entity.User;
import com.practice.likelionhackathoncesco.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Slf4j
@DataJpaTest
@AutoConfigureJson
@Import({PostService.class, PostMapper.class, PostCursorCodec.class})
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PostServiceQueryCountTest {

  private static final String ROAD_CODE = "117104169350";
  private static final String BUILDING_NUMBER = "29";
  private static final int POST_COUNT = 3;

  @Autowired private PostService postService;
  @Autowired private UserRepository userRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private CommentRepository commentRepository;
  @Autowired private LikeRepository likeRepository;
  @Autowired private EntityManagerFactory emf;
  @Autowired private TestEntityManager testEntityManager;

  @BeforeEach
  void beforeSetUp() {

    User user =
        userRepository.save(User.builder().username("cesco").payStatus(PayStatus.UNPAID).build());

    for (int i = 1; i <= POST_COUNT; i++) {
      Post post =
          postRepository.save(
              Post.builder()
                  .content("게시글 " + i)
                  .roadCode(ROAD_CODE)
                  .buildingNumber(BUILDING_NUMBER)
                  .user(user)
                  .build());

      commentRepository.save(Comment.builder().content("댓글 " + i).user(user).post(post).build());

      likeRepository.save(Like.builder().user(user).post(post).build());
    }

    testEntityManager.flush();
    testEntityManager.clear();
  }

  @Test
  void 게시글_3개_최신순_조회시_쿼리_3번_발생() {

    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();

    postService.getAllPostsByRoadCodeAndBuildingNumber(
        ROAD_CODE, BUILDING_NUMBER, "latest", null, 20);

    long queryCount = stats.getPrepareStatementCount();
    log.info("[리팩 후 쿼리 수 측정] 게시글 {}개 최신순 조회 시 실행된 쿼리 수: {}번 (예상: {}번)", POST_COUNT, queryCount, 3);

    assertThat(queryCount).isEqualTo(3);
  }

  @Test
  void 게시글_3개_인기순_조회시_쿼리_3번_발생() {

    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();

    postService.getAllPostsByRoadCodeAndBuildingNumber(
        ROAD_CODE, BUILDING_NUMBER, "popular", null, 20);

    long queryCount = stats.getPrepareStatementCount();
    log.info("[리팩 후 쿼리 수 측정] 게시글 {}개 인기순 조회 시 실행된 쿼리 수: {}번 (예상: {}번)", POST_COUNT, queryCount, 3);

    assertThat(queryCount).isEqualTo(3);
  }
}
