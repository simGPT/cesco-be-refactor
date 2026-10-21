package com.practice.likelionhackathoncesco.domain.post.service;

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
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DataJpaTest // @Transactional 이미 포함되어 있음
@Import({PostService.class, PostMapper.class})
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PostServiceQueryCountTest {

    private static final String ROAD_CODE = "117104169350";
    private static final String BUILDING_NUMBER = "29";
    private static final int POST_COUNT = 3;

    @Autowired
    private PostService postService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private LikeRepository likeRepository;
    @Autowired
    private EntityManagerFactory emf;
    @Autowired
    private TestEntityManager testEntityManager;

    @BeforeEach
        // 각 테스트 코드가 실행되기 전에 먼저 자동으로 실행
    void beforeSetUp() {

        // 사용자 데이터 미리 넣기
        User user =
                userRepository.save(User.builder().username("cesco").payStatus(PayStatus.UNPAID).build());

        // post, commemt, like 각각 3개씩 미리 데이터 넣어놓기
        for (int i = 1; i <= POST_COUNT; i++) {
            Post post =
                    postRepository.save(
                            Post.builder()
                                    .content("게시글 " + i)
                                    .roadCode(ROAD_CODE)
                                    .buildingNumber(BUILDING_NUMBER)
                                    .user(user)
                                    .build());

            commentRepository.save(
                    Comment.builder().content("댓글 " + i).user(user).post(post).build());

            likeRepository.save(Like.builder().user(user).post(post).build());
        }

        testEntityManager.flush(); // 실제 DB에 반영
        testEntityManager.clear();
    }

    @Test
    void 게시글_3개_조회시_2N_plus_1_쿼리_발생() {

        // 서비스가 아니라 테스트코드 작성을 위해 미리 위에서 집어넣은 쿼리 개수는 측정하지 않기 위해 clear
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        // 조회 로직 수행
        postService.getAllPostsByRoadCodeAndBuildingNumber(ROAD_CODE, BUILDING_NUMBER);

        // 쿼리 수 측정 및 기록
        long queryCount = stats.getPrepareStatementCount();
        log.info("[쿼리 수 측정] 게시글 {}개 조회 시 실행된 쿼리 수: {}번 (예상: {}번)", POST_COUNT, queryCount, 1 + 2 * POST_COUNT);

        // 실제 쿼리 수가 2N+1이 맞는지 검증
        assertThat(queryCount).isEqualTo(1 + 2L * POST_COUNT); // 1 + 2*3 = 7
    }
}
