package com.practice.likelionhackathoncesco.domain.analysisreport.calculator;

import com.practice.likelionhackathoncesco.infra.openai.dto.request.GptAnalysisRequest;
import com.practice.likelionhackathoncesco.infra.openai.dto.response.GptDeptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class InsuranceScoreCalculator {

    private final GptAnalysisRequest gptAnalysisRequest;
    private final GptDeptResponse gptDeptResponse;
    private final int dangerNum;
    private final int officialPrice;

    public int calculateInsuranceScore() {

        int insuranceData;
        long dept = gptDeptResponse.getDept();
        long isMonthlyDeposit =
                Math.round(
                        gptAnalysisRequest.getMonthlyRent() * 12 * 100 / 6
                                + gptAnalysisRequest.getDeposit()); // 전월세 변환율 적용한 보증금

        // 보증보험 가입 가능여부 판단
        // 전세일때
        if (gptAnalysisRequest.getIsMonthlyRent() == 0) {
            if (dept >= Math.round(officialPrice * 1.3 * 0.6)
                    || dept + gptAnalysisRequest.getDeposit() > Math.round(officialPrice * 1.3 * 0.9)) {
                insuranceData = 0;
            } else { // insuranceDate == 1 일때
                if (dangerNum <= 0) { // 아무리 insuranceDate == 1 이어도 dangerNum <= 0 이면 insuranceData도 0
                    insuranceData = 0;
                } else {
                    insuranceData = 1;
                }
            }
        } else { // 월세일때
            if (isMonthlyDeposit > 700000000
                    || dept > Math.round(officialPrice * 1.3 * 0.6)
                    || dept + isMonthlyDeposit > officialPrice * 1.3 * 0.9) {
                insuranceData = 0;
            } else {
                if (dangerNum <= 0) { // 아무리 insuranceDate == 1 이어도 dangerNum <= 0 이면 insuranceData도 0
                    insuranceData = 0;
                } else {
                    insuranceData = 1;
                }
            }
        }
        return insuranceData;
    }
}
