package com.practice.likelionhackathoncesco.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.practice.likelionhackathoncesco.domain.post.dto.cursor.PostCursor;
import com.practice.likelionhackathoncesco.global.exception.CustomException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostCursorCodecTest {

  private PostCursorCodec postCursorCodec;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule()); // LocalDateTime을 인코딩/디코딩 하기 위해 필요함
    postCursorCodec = new PostCursorCodec(objectMapper);
  }

  @Test
  void 최신순_커서_인코딩_후_디코딩하면_원래_값과_동일하다() {
    PostCursor cursor =
        new PostCursor("latest", LocalDateTime.of(2026, 11, 17, 12, 0, 0), null, 1L);

    String encoded = postCursorCodec.encode(cursor);
    PostCursor decoded = postCursorCodec.decode(encoded);

    // 디코딩한 java 객체 각 필드 값이 동일한지 검증
    assertThat(decoded.getSort()).isEqualTo(cursor.getSort());
    assertThat(decoded.getCreatedAt()).isEqualTo(cursor.getCreatedAt());
    assertThat(decoded.getLikeCount()).isNull();
    assertThat(decoded.getPostId()).isEqualTo(cursor.getPostId());
  }

  @Test
  void 인기순_커서_인코딩_후_디코딩하면_원래_값과_동일하다() {
    PostCursor cursor = new PostCursor("popular", null, 100, 2L);

    String encoded = postCursorCodec.encode(cursor);
    PostCursor decoded = postCursorCodec.decode(encoded);

    assertThat(decoded.getSort()).isEqualTo(cursor.getSort());
    assertThat(decoded.getCreatedAt()).isNull();
    assertThat(decoded.getLikeCount()).isEqualTo(cursor.getLikeCount());
    assertThat(decoded.getPostId()).isEqualTo(cursor.getPostId());
  }

  @Test
  void 깨진_문자열_디코딩시_예외가_발생한다() {
    assertThatThrownBy(() -> postCursorCodec.decode("invalid-cursor-string!!"))
        .isInstanceOf(CustomException.class);
  }
}
