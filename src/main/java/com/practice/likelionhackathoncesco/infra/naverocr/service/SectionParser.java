package com.practice.likelionhackathoncesco.infra.naverocr.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SectionParser {

  private final OcrTextCleaner ocrTextCleaner;

  // 마커 기반 섹션 파싱
  public Map<String, List<String>> parseByMarkers(List<String> allTexts) {
    // 임시 저장용 맵 (파싱 순서대로 저장)
    Map<String, List<String>> tempSections = new LinkedHashMap<>();

    String currentSection = "표제부"; // 기본적으로 표제부로 시작
    List<String> currentTexts = new ArrayList<>();

    for (int i = 0; i < allTexts.size(); i++) {
      String text = allTexts.get(i).trim();

      // 섹션 마커 감지
      String detectedMarker = detectSectionMarker(i, allTexts);

      if (detectedMarker != null) {
        // 이전 섹션 저장
        if (!currentTexts.isEmpty()) {
          List<String> cleanedTexts = ocrTextCleaner.cleanTexts(currentTexts);
          if (!cleanedTexts.isEmpty()) {
            tempSections.put(currentSection, cleanedTexts);
            log.info("섹션 완료: {}, 텍스트 수: {}", currentSection, cleanedTexts.size());
          }
        }

        // 새 섹션 시작
        currentSection = detectedMarker;
        currentTexts = new ArrayList<>();
        log.info("새 섹션 시작: {} (인덱스: {})", currentSection, i);

        // 마커 텍스트들 건너뛰기
        i = skipMarkerTexts(i, allTexts, detectedMarker);
        continue;
      }

      // 현재 섹션에 텍스트 추가
      if (!text.isEmpty()) {
        currentTexts.add(text);
      }
    }

    // 마지막 섹션 저장
    if (!currentTexts.isEmpty()) {
      List<String> cleanedTexts = ocrTextCleaner.cleanTexts(currentTexts);
      if (!cleanedTexts.isEmpty()) {
        tempSections.put(currentSection, cleanedTexts);
        log.info("마지막 섹션 완료: {}, 텍스트 수: {}", currentSection, cleanedTexts.size());
      }
    }

    // 원하는 순서대로 재정렬: 표제부 -> 갑구 -> 을구
    Map<String, List<String>> orderedSections = new LinkedHashMap<>();

    // 1. 표제부 먼저
    if (tempSections.containsKey("표제부")) {
      orderedSections.put("표제부", tempSections.get("표제부"));
      log.info("표제부 섹션 순서 배치 완료");
    }

    // 2. 갑구 두 번째
    if (tempSections.containsKey("갑구")) {
      orderedSections.put("갑구", tempSections.get("갑구"));
      log.info("갑구 섹션 순서 배치 완료");
    }

    // 3. 을구 마지막
    if (tempSections.containsKey("을구")) {
      orderedSections.put("을구", tempSections.get("을구"));
      log.info("을구 섹션 순서 배치 완료");
    }

    log.info("섹션 순서 재정렬 완료: {}", orderedSections.keySet());
    return orderedSections;
  }

  // 섹션 마커 감지 (【 표 제 부 】, 【 갑 구 】, 【 을 구 】)
  private String detectSectionMarker(int startIndex, List<String> texts) {
    // 최소 검사 범위 확보
    if (startIndex >= texts.size()) {
      return null;
    }

    // "( 소유권에" + "관한 사항 )" 패턴으로 갑구 확실히 감지 (최우선)
    if (isOwnershipRightsPattern(startIndex, texts)) {
      log.info("갑구 마커 발견 - '소유권에 관한 사항' 패턴 (인덱스: {})", startIndex);
      return "갑구";
    }

    // "을" + "구" 패턴으로 을구 감지 (갑구보다 우선)
    if (isEulguMarkerPattern(startIndex, texts)) {
      log.info("을구 마커 발견 (인덱스: {})", startIndex);
      return "을구";
    }

    // 갑구 마커 감지 (표제부보다 먼저)
    if (isGapguMarkerPattern(startIndex, texts)) {
      log.info("갑구 마커 발견 (인덱스: {})", startIndex);
      return "갑구";
    }

    // 표제부 마커 감지 (가장 나중에 확인)
    String currentText = texts.get(startIndex).trim();
    if (currentText.equals("표제부")) {
      log.info("표제부 마커 발견 (인덱스: {})", startIndex);
      return "표제부";
    }

    return null;
  }

  // "( 소유권에" + "관한 사항 )" 패턴 감지 (갑구의 확실한 신호)
  private boolean isOwnershipRightsPattern(int startIndex, List<String> texts) {
    // 현재 위치부터 앞뒤로 검색
    int searchStart = Math.max(0, startIndex - 2);
    int searchEnd = Math.min(texts.size() - 1, startIndex + 10);

    log.debug("소유권에 관한 사항 패턴 검사 범위: {} ~ {}", searchStart, searchEnd);

    boolean foundOwnership = false;
    boolean foundRights = false;
    boolean foundMatter = false;

    // "소유권에", "관한", "사항" 패턴 찾기
    for (int i = searchStart; i <= searchEnd; i++) {
      String token = texts.get(i).trim();

      if (token.contains("소유권에") || token.equals("( 소유권에") || token.equals("소유권에")) {
        foundOwnership = true;
        log.debug("'소유권에' 토큰 발견: 인덱스 {}, 텍스트: '{}'", i, token);
      }

      if (token.contains("관한") || token.equals("관한")) {
        foundRights = true;
        log.debug("'관한' 토큰 발견: 인덱스 {}, 텍스트: '{}'", i, token);
      }

      if (token.contains("사항")
          || token.equals("사항")
          || token.equals("사항 )")
          || token.contains("사항 )")) {
        foundMatter = true;
        log.debug("'사항' 토큰 발견: 인덱스 {}, 텍스트: '{}'", i, token);
      }
    }

    // 세 키워드가 모두 발견되고 "이외의"가 없으면 갑구
    if (foundOwnership && foundRights && foundMatter) {
      // "이외의" 키워드가 주변에 있는지 확인 (을구와 구분)
      boolean hasEoiOe = false;
      for (int i = searchStart; i <= searchEnd; i++) {
        String token = texts.get(i).trim();
        if (token.contains("이외의") || token.contains("이외")) {
          hasEoiOe = true;
          log.debug("'이외의' 키워드 발견 - 을구로 판단");
          break;
        }
      }

      if (!hasEoiOe) {
        log.debug("갑구 확정: '소유권에 관한 사항' 패턴 (이외의 키워드 없음)");
        return true;
      }
    }

    return false;
  }

  // 을구 마커 패턴 확인
  private boolean isEulguMarkerPattern(int startIndex, List<String> texts) {
    // 기본 범위 체크
    if (startIndex + 1 >= texts.size()) {
      return false;
    }

    String token1 = texts.get(startIndex).trim();
    String token2 = texts.get(startIndex + 1).trim();

    log.debug("을구 패턴 검사: '{}' + '{}'", token1, token2);

    // 1. "을" + "구" 직접 패턴
    if (token1.equals("을") && token2.equals("구")) {
      log.debug("을구 직접 패턴 발견: '{}' + '{}'", token1, token2);

      // 추가 검증: "소유권 이외의" 패턴이 근처에 있는지 확인
      boolean hasEulguKeywords = false;
      for (int i = startIndex; i < Math.min(startIndex + 15, texts.size()); i++) {
        String checkText = texts.get(i).trim();
        if ((checkText.contains("소유권") && checkText.contains("이외의"))
            || checkText.contains("근저당권")
            || checkText.contains("임차권")
            || checkText.contains("전세권")
            || checkText.contains("채권최고액")) {
          hasEulguKeywords = true;
          log.debug("을구 키워드 확인: '{}'", checkText);
          break;
        }
      }

      if (hasEulguKeywords) {
        return true;
      }

      // 키워드가 없어도 "을" + "구" 패턴이 명확하면 을구로 판단
      log.debug("을구 패턴 확정: 명확한 '을' + '구' 조합");
      return true;
    }

    // 2. "을구" 단일 토큰
    if (token1.equals("을구")) {
      log.debug("을구 단일 토큰 발견: '{}'", token1);
      return true;
    }

    return false;
  }

  // 갑구 마커 패턴 확인 - 기존 갑구 패턴들
  private boolean isGapguMarkerPattern(int startIndex, List<String> texts) {
    // 기본 범위 체크
    if (startIndex + 1 >= texts.size()) {
      return false;
    }

    String token1 = texts.get(startIndex).trim();
    String token2 = texts.get(startIndex + 1).trim();

    log.debug("갑구 패턴 검사: '{}' + '{}'", token1, token2);

    // "갑" + "구" 직접 패턴
    if (token1.equals("갑") && token2.equals("구")) {
      log.debug("갑구 직접 패턴 발견: '{}' + '{}'", token1, token2);
      return true;
    }

    // "갑구" 단일 토큰
    if (token1.equals("갑구")) {
      log.debug("갑구 단일 토큰 발견: '{}'", token1);
      return true;
    }

    // "| 갑" + "구" 패턴
    if ((token1.equals("|") || token1.contains("갑")) && token2.equals("구")) {
      log.debug("갑구 특수 패턴: '{}' + '{}'", token1, token2);
      return true;
    }

    return false;
  }

  // 마커 텍스트들 건너뛰기
  private int skipMarkerTexts(int startIndex, List<String> texts, String sectionType) {
    int skipCount = 1; // 기본적으로 1개 건너뛰기

    if ("갑구".equals(sectionType)) {
      // "| 갑" + "구" 패턴인 경우 2개 건너뛰기
      if (startIndex + 1 < texts.size() && texts.get(startIndex).contains("|")
          || texts.get(startIndex).equals("갑")) {
        skipCount = 2;
      }
    } else if ("을구".equals(sectionType)) {
      // "을" + "구" 패턴인 경우 2개 건너뛰기
      if (startIndex + 1 < texts.size()
          && texts.get(startIndex).equals("을")
          && texts.get(startIndex + 1).equals("구")) {
        skipCount = 2;
      }
    }

    return Math.min(startIndex + skipCount, texts.size() - 1);
  }
}
