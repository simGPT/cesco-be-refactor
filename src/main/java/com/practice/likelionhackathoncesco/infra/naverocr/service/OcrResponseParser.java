package com.practice.likelionhackathoncesco.infra.naverocr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.likelionhackathoncesco.domain.analysisreport.entity.ProcessingStatus;
import com.practice.likelionhackathoncesco.infra.naverocr.dto.response.OcrResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OcrResponseParser {

  private final SectionParser sectionParser;

  protected OcrResponse parseResponse(ResponseEntity<String> response) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode root = objectMapper.readTree(response.getBody());

    JsonNode imagesNode = root.path("images"); // 이미지 배열로 저장되어 있음
    log.info("총 페이지 수: {}", imagesNode.size());

    // 모든 텍스트를 순서대로 수집
    List<String> allTexts = new ArrayList<>();

    // 모든 페이지를 순회하여 텍스트 수집
    for (int pageIndex = 0; pageIndex < imagesNode.size(); pageIndex++) {
      JsonNode currentPage = imagesNode.get(pageIndex);
      JsonNode tablesNode = currentPage.path("tables");

      log.info("페이지 {} 처리 중, 테이블 수: {}", pageIndex + 1, tablesNode.size());

      if (tablesNode.isArray()) {
        for (JsonNode table : tablesNode) {
          List<String> tableTexts = extractTextsFromTable(table);
          allTexts.addAll(tableTexts);
          log.info("테이블에서 {}개 텍스트 추출", tableTexts.size());
        }
      }
    }

    log.info("전체 텍스트 수집 완료: {}개", allTexts.size());

    // 마커 기반으로 섹션 구분 (표제부, 갑구, 을구)
    Map<String, List<String>> sections = sectionParser.parseByMarkers(allTexts);

    log.info("전체 처리 완료 - 총 섹션 수: {}", sections.size());

    return OcrResponse.builder()
        .sections(sections)
        .processingStatus(ProcessingStatus.OCR_COMPLETED)
        .build();
  }

  // 테이블에서 텍스트 추출
  private List<String> extractTextsFromTable(JsonNode table) {
    List<String> inferTexts = new ArrayList<>();
    JsonNode cells = table.path("cells");

    if (cells.isArray()) {
      for (JsonNode cell : cells) {
        JsonNode cellTextLines = cell.path("cellTextLines");
        if (cellTextLines.isArray()) {
          for (JsonNode line : cellTextLines) {
            JsonNode cellWords = line.path("cellWords");
            if (cellWords.isArray()) {
              for (JsonNode word : cellWords) {
                String text = word.path("inferText").asText().trim();
                if (!text.isEmpty()) {
                  inferTexts.add(text);
                }
              }
            }
          }
        }
      }
    }
    return inferTexts;
  }
}
