package com.practice.likelionhackathoncesco.domain.post.dto.cursor;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostCursor {
  private String sort;
  private LocalDateTime createdAt; // 인기순 일때 null
  private Integer likeCount; // 최신순 일때 null
  private Long postId;
}
