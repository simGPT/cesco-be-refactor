package com.practice.likelionhackathoncesco.infra.naverocr.service;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OcrTextCleaner {

  // 텍스트 정리
  public List<String> cleanTexts(List<String> texts) {
    return texts.stream()
        .filter(text -> text != null && !text.trim().isEmpty())
        .filter(text -> !isNoiseText(text))
        .map(String::trim)
        .collect(Collectors.toList());
  }

  // 노이즈 텍스트 필터링
  private boolean isNoiseText(String text) {
    String trimmed = text.trim();
    return trimmed.equals("(")
        || trimmed.equals(")")
        || trimmed.equals("】")
        || trimmed.equals("【")
        || trimmed.equals("[")
        || trimmed.equals("]")
        || trimmed.equals("|")
        || trimmed.equals("■")
        || trimmed.equals("▣")
        || (trimmed.length() == 1
            && !Character.isDigit(trimmed.charAt(0))
            && !trimmed.matches("[가-힣a-zA-Z]")); // 한글, 영문, 숫자가 아닌 한 글자
  }
}
