package com.practice.likelionhackathoncesco.domain.post.mapper;

import com.practice.likelionhackathoncesco.domain.post.dto.response.PostResponse;
import com.practice.likelionhackathoncesco.domain.post.entity.Post;
import org.springframework.stereotype.Component;

@Component
public class PostMapper {

    public PostResponse toPostResponse(Post post, long likeCount, long commentCount, boolean likeByUser) {
        return PostResponse.builder()
                .postId(post.getPostId())
                .content(post.getContent())
                .likeCount(likeCount)
                .commentCount(commentCount)
                .likeByUser(likeByUser) // 이거 필요없는 부분에서는 false로 호출
                .userId(post.getUser().getUserId())
                .createdAt(post.getCreatedAt())
                .modifiedAt(post.getModifiedAt())
                .build();
    }
}
