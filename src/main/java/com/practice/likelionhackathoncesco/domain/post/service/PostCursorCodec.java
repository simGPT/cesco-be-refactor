package com.practice.likelionhackathoncesco.domain.post.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.likelionhackathoncesco.domain.post.dto.cursor.PostCursor;
import com.practice.likelionhackathoncesco.domain.post.exception.PostErrorCode;
import com.practice.likelionhackathoncesco.global.exception.CustomException;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostCursorCodec {

    private final ObjectMapper objectMapper;

    // 인코딩 : 직렬화 → Base64
    public String encode(PostCursor postCursor) {
        try {
            String json = objectMapper.writeValueAsString(postCursor);
            return Base64.getUrlEncoder().encodeToString(json.getBytes());
        } catch (Exception e) {
            throw new CustomException(PostErrorCode.INVALID_CURSOR);
        }
    }

    // 디코딩 : Base64 → 역직렬화
    public PostCursor decode(String cursorString) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursorString);
            return objectMapper.readValue(decoded, PostCursor.class);
        } catch (Exception e) {
            throw new CustomException(PostErrorCode.INVALID_CURSOR);
        }
    }
}
