package com.practice.likelionhackathoncesco.domain.analysisreport.calculator;

import com.practice.likelionhackathoncesco.infra.openai.dto.request.GptAnalysisRequest;
import com.practice.likelionhackathoncesco.infra.openai.dto.response.GptDeptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SafetyScoreCalculator {

  private final GptAnalysisRequest gptAnalysisRequest;
  private final GptDeptResponse gptDeptResponse;

  public int calculateOfficialPrice() {
    int officalPrice = 340000000;

    if (gptAnalysisRequest.getIsExample() == 1) { // 예시: 안전
      officalPrice = 129000000;
    } else if (gptAnalysisRequest.getIsExample() == 2) { // 예시: 불안
      officalPrice = 700000000;
    } else if (gptAnalysisRequest.getIsExample() == 3) { // 예시: 위험
      officalPrice = 150000000;
    }

    return officalPrice;
  }

  public int calculateDangerNum() {

    int officalPrice = calculateOfficialPrice();
    long dept = gptDeptResponse.getDept();
    int dangerNum = gptDeptResponse.getDangerNum();

    // 전월세 지수 판단하기
    // dangerNum <= 0 이면 safetyData 따질것도 없이 dangerNum 유지
    // dangerNum == 1 이고 officalPrice > dept 이면 dangerNum 유지
    // dangerNum == 1 이고 officalPrice <= dept 일때만 dangerNum = 0 으로 변경!!
    if (officalPrice <= dept) {
      dangerNum = 0;
    }
    log.info("[SafetyScoreCalculator] dangerNum: {}", dangerNum);
    return dangerNum;
  }

  // 최종 안전지수 계산 메서드
  public Double calculateSafetyScore() {

    int officalPrice = calculateOfficialPrice();
    long dept = gptDeptResponse.getDept();
    int dangerNum = calculateDangerNum();
    Double realSafetyScore;
    Double safetyScore;

    if (dangerNum == 1) { // 안전 또는 불안 범위
      if ((officalPrice - dept) >= gptAnalysisRequest.getDeposit()) { // 안전 : 7~10점
        realSafetyScore =
            7.0
                + 3
                    * ((double) (officalPrice - dept - gptAnalysisRequest.getDeposit()))
                    / (officalPrice - dept);

      } else { // 불안 : 3~7점
        realSafetyScore =
            3.0
                + 4
                    * (1
                        - ((double) (gptAnalysisRequest.getDeposit() - officalPrice + dept))
                            / (officalPrice - dept));
      }
    } else { // 위험 : 0~3점
      realSafetyScore = 3.0 + dangerNum;
    }

    log.info("[SafetyScoreCalculator] realSafetyScore 계산 성공 : " + realSafetyScore);

    // 안전점수 최소 0.0 , 최대 10.0 으로 설정
    realSafetyScore = Math.max(0.0, Math.min(10.0, realSafetyScore));

    safetyScore = Math.round(realSafetyScore * 10) / 10.0;
    log.info("[SafetyScoreCalculator] safetyScore 소수점 첫째자리로 계산 성공: " + safetyScore);

    return safetyScore;
  }

  public String calculateSafetyScoreStatus(double safetyScore) {

    String safetyScoreStatus;

    if (safetyScore >= 7) {
      safetyScoreStatus = "안전";
    } else if (safetyScore >= 3) {
      safetyScoreStatus = "불안";
    } else {
      safetyScoreStatus = "위험";
    }
    log.info("[SafetyScoreCalculator] 최종 safetyScoreStatus: {}", safetyScoreStatus);

    return safetyScoreStatus;
  }
}
