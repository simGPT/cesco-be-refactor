package com.practice.likelionhackathoncesco.domain.post.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(title = "PostPageResponse DTO", description = "게시글 목록 조회 응답(커서 페이지네이션)")
public class PostPageResponse {

  @Schema(description = "페이지네이션에서 한번에 반환하는 게시글 목록")
  private List<PostResponse> postList; // 한번에 반환하는 게시글 목록

  @Schema(
      description = "커서 객체 인코딩한 Base64 문자열",
      example =
          "eyJzb3J0IjoibGF0ZXN0IiwiY3JlYXRlZEF0IjoiMjAyNi0xMS0xN1QxMjowMDowMCIsImxpa2VDb3VudCI6bnVsbCwicG9zdElkIjoyMH0=")
  private String nextCursor;

  @Schema(description = "다음으로 조회할 게시글의 존재 여부", example = "true")
  private boolean hasNext;
}
