package com.practice.likelionhackathoncesco.infra.openai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.likelionhackathoncesco.global.exception.CustomException;
import com.practice.likelionhackathoncesco.infra.naverocr.dto.response.OcrResponse;
import com.practice.likelionhackathoncesco.infra.naverocr.service.NaverOcrService;
import com.practice.likelionhackathoncesco.infra.openai.dto.request.GptAnalysisRequest;
import com.practice.likelionhackathoncesco.infra.openai.dto.request.GptSecRequest;
import com.practice.likelionhackathoncesco.infra.openai.dto.response.GptDeptResponse;
import com.practice.likelionhackathoncesco.infra.openai.dto.response.GptResponse;
import com.practice.likelionhackathoncesco.infra.openai.exception.GptErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GptService {

    private final ObjectMapper objectMapper;
    private final NaverOcrService naverOcrService;

    // 근저당 총액을 알아내기 위한 프롬프트 생성 메소드
    public List<Map<String, String>> createPromptForDept(
            GptAnalysisRequest gptAnalysisRequest, Long reportId) throws JsonProcessingException {

        Integer officalPrice = 340000000;

        if (gptAnalysisRequest.getIsExample() == 1) { // 예시: 안전
            officalPrice = 129000000;
        } else if (gptAnalysisRequest.getIsExample() == 2) { // 예시: 불안
            officalPrice = 700000000;
        } else if (gptAnalysisRequest.getIsExample() == 3) { // 예시: 위험
            officalPrice = 150000000;
        }

        // ocr로 추출한 택스트 바로 가져오기
        OcrResponse ocrResponse = naverOcrService.extractText(reportId);
        Map<String, List<String>> text = ocrResponse.getSections();
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonText = objectMapper.writeValueAsString(text);

        List<Map<String, String>> prompts = new ArrayList<>();

        // 전월세 여부 문자열로 변환
        String rentType = gptAnalysisRequest.getIsMonthlyRent() == 1 ? "월세" : "전세";

        // gpt에게 행동지침을 주는 역할의 프롬프트
        prompts.add(Map.of("role", "system", "content", "너는 부동산 등기부 등본을 분석해서 위험요소를 판단하는 전문가야."));

        prompts.add(
                Map.of(
                        "role",
                        "user",
                        "content",
                        String.format(
                                """
                                        이 부동산은 %s 계약을 하려는 중이야.
                                        아래는 ocr로 추출한 부동산 등기부 등본 택스트야.
                                        
                                        [
                                        %s
                                        ]
                                        
                                        위에 ocr로 추출한 텍스트를 보고
                                        1. 을구에서 말소되지 않은 근저당권 설정 내역을 모두 찾아서, 모든 근저당 채권최고액을 합산한 근저당 총액을 'dept'에 넣어서 정확히 추출해줘. 근저당이 없는 경우에는 'dept'는 0을 반환해.
                                        2. 다음은 ‘dangerNum’에 해당하는 점수총합을 산출하는 방식이다.
                                          ‘dangerNum’은 1단계 무조건 안전 처리 조건과 2단계 점수 산정 규칙에 따라 계산된 총합 정수를 의미하며, 응답 JSON에서 ‘dangerNum’ 필드로 반드시 포함되어야 한다. ocr로 추출한 부동산 등기부 등본 텍스트를 보고 **다음조건을 만족하면 'dangerNum'은 숫자 1이어야만해**:
                                          ### 1단계: 무조건 안전 처리 규칙(반드시 '갑구' 영역에만 해당하는 내용입니다)
                                          - '갑구'영역 내에 "가처분, 가등기, 가압류, 압류" 네 가지 항목이 전혀 없거나
                                          - 모든 "가처분, 가등기, 가압류, 압류" 네 가지 항목에 대해 말소(말소, 말소기입, 해지) 표시가 되어 있다면, 다른 어떠한 조건도 보지 말고 무조건 숫자 1만 반환한다.
                                        
                                          ### 2단계: 1단계 조건 미충족 시 점수 산정
                                          아래 조건을 엄격히 적용해 점수를 계산하고, 그 **합계 값 하나만 ‘dangerNum’으로 반환**해줘. 단, 반드시 "갑구"에 한정해서만 검토한다. "을구(근저당, 저당권 등)"는 점수 계산과 전혀 무관하다.:
                                          - '말소되지 않은 가처분'이 있으면 0
                                          - '말소되지 않은 가등기'가 있으면 0
                                          - '말소되지 않은 가압류'가 있으면 -1
                                          - '말소되지 않은 압류'가 있으면 -2
                                        
                                        여기서 "말소되지 않은" 상태란 다음을 의미한다.
                                        여기서 "말소되지 않은" 상태란 다음을 의미한다:
                                        - OCR 텍스트에 '가처분','가등기','가압류','압류'라는 단어가 반드시 존재하면서
                                        - 갑구 영역 내 텍스트 안에 해당 항목에 대해 '말소', '말소기입','해지' 등의 단어가 전혀 없는 경우만 "말소되지 않음으로 간주한다.
                                        - 만약 해당 항목이 존재하지만 말소 관련 단어가 함께 기재되어 있다면 반드시 “존재하지 않는 것”으로 처리해야 한다.
                                        
                                        이제 택스트를 분석해서 결과를 다음 JSON 형식으로 응답해주고, 이외의 다른 설명은 하지 마:
                                        {
                                          "dept":"말소되지 않은 근저당의 총액"
                                          "dangerNum":"점수총합" 예시 : "-2"
                                        }
                                        """,
                                rentType, jsonText)));
        return prompts;
    }

    // 근저당 총액을 파싱해서 GptDeptResponse로 반환하는 메소드
    public GptDeptResponse parseDeptResponse(String deptContent) {
        try {
            JsonNode node = objectMapper.readTree(deptContent);

            // dept 파싱
            String originDept = node.get("dept").asText();
            String cleanedDept = originDept.replaceAll("[^0-9]", ""); // 숫자만 남기도록 수정

            Long dept;
            if (cleanedDept.isEmpty()) {
                dept = 0L;
            } else {
                dept = Long.parseLong(cleanedDept);
            }

            // dangerNum 파싱
            Integer dangerNum = 0;
            if (node.has("dangerNum") && !node.get("dangerNum").isNull()) {
                dangerNum = node.get("dangerNum").asInt();
            }

            return new GptDeptResponse(dept, dangerNum);

        } catch (JsonProcessingException e) {
            throw new CustomException(GptErrorCode.GPT_RESPONSE_PARSING_FAILED);
        }
    }

    // -----------------------------

    // 프롬프트 생성 메소드
    public List<Map<String, String>> createPrompt(
            GptAnalysisRequest gptAnalysisRequest, GptSecRequest gptSecRequest, Long reportId)
            throws JsonProcessingException {

        // ocr로 추출한 택스트 바로 가져오기
        OcrResponse ocrResponse = naverOcrService.extractText(reportId);
        Map<String, List<String>> text = ocrResponse.getSections();
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonText = objectMapper.writeValueAsString(text);

        List<Map<String, String>> prompts = new ArrayList<>();

        // 전월세 여부 문자열로 변환
        String rentType = gptAnalysisRequest.getIsMonthlyRent() == 1 ? "월세" : "전세";

        // gpt에게 행동지침을 주는 역할의 프롬프트
        prompts.add(Map.of("role", "system", "content", "너는 부동산 등기부 등본을 분석해서 위험요소를 판단하는 전문가야."));

        // gpt에게 사용자가 질문하거나 지시하는 메시지 -> JSON 형식으로 응답해달라는 것 반드시 명시
        prompts.add(
                Map.of(
                        "role",
                        "user",
                        "content",
                        String.format(
                                """
                                        이 부동산은 %s 계약을 하려는 중이야.
                                        아래는 ocr로 추출한 부동산 등기부 등본 택스트야.
                                        
                                        등기부등본 택스트를 분석해서 아래 내용을 정확히 순서대로 작성해줘:
                                        현재 이 부동산은 계약하기에 %s한 매물이야
                                        1. 현재 이 거래가 %s하다고 적어서 'summary'에 넣어줘. 그리고 아래에 너가 작성한 'safetyDescription'과 'insuranceDescription'을 세 줄 정도로 요약해서 'summary'에 넣어줘 **단, 다른 조건과는 관계없이 반드시 safetyScoreStatus값인 '%s'만 보고 적어야해.**
                                        2. **이 부동산이 %s한 이유를 다음을 기반으로 10줄 정도로 설명해서 'safetyDescription'에 넣어줘:**
                                          - %s가 '안전'이라면 해당 부동산은 가처분, 가등기, 가압류, 압류가 아예 존재하지 않거나 이미 말소된 부동산이야.
                                          - %s가 '불안'이라면 해당 부동산은 가처분, 가등기, 가압류, 압류가 아예 존재하지 않거나 이미 말소된 부동산이야. 그리고 근저당 총액이 높은편인 부동산이야.
                                          - %s가 '위험'이라면 해당 부동산은 가처분, 가등기, 가압류, 압류 중 하나 이상 말소되지 않고 존재하고 있거나 혹은 근저당 총액이 공시가격보다 높은 부동산이야.
                                          - 안전지수는 10점 만점에 %.1f야
                                        """,
                                rentType,
                                gptSecRequest.getSafetyScoreStatus(),
                                gptSecRequest.getSafetyScoreStatus(),
                                gptSecRequest.getSafetyScoreStatus(),
                                gptSecRequest.getSafetyScoreStatus(),
                                gptSecRequest.getSafetyScoreStatus(),
                                gptSecRequest.getSafetyScoreStatus(),
                                gptSecRequest.getSafetyScoreStatus(),
                                gptSecRequest.getSafetyScore()))); // 위치 어디로 바꾸지?

        if (gptAnalysisRequest.getIsMonthlyRent() == 0) { // 전세 계약의 경우

            prompts.add(
                    Map.of(
                            "role",
                            "user",
                            "content",
                            String.format(
                                    """
                                            보증보험 가입이 가능한 다음 조건들을 보고 보증보험 가입 가능여부를 명확히 판단하고 그 판단의 이유를 다음을 기반으로 10줄 정도로 작성해서 insuranceDescription에 넣어줘:
                                            - insuranceData 값인 %d가 1인 경우만 보증보험 가입이 가능해.(왜냐하면 근저당, 시세, 보증금을 비교했을때 보증보험 가입 가능 여부를 충족해야지만 insuranceData가 1일 수 있기 때문)
                                            - 이 부동산이 아파트, 주거용 오피스텔, 연립·다세대주택, 단독·다중·다가구주택, 노인복지주택 중 하나여야만 해.
                                            
                                            이제 아래 등기부등본 택스트를 분석해서 각 조건을 엄격히 적용하여 판단하고, 다음 JSON 형식으로 결과를 응답:
                                            {
                                              "address":"표제부에 있는 이 부동산의 주소"
                                              "summary":"거래에 대한 세줄 요약"
                                              "safetyDescription":"두줄 요약 판단의 이유 10줄 정도를 공인중개사처럼 부드러운 상담톤으로 듣는 사람이 최대한 이해하기 쉽게 해줘"
                                              "insuranceDescription":"보증보험 가입 가능 여부를 조건에 맞게 명확히 판단하여 작성해. 가입 불가 시에는 반드시 ‘보증보험 가입이 불가합니다’라는 문장 포함해. 가입 가능 시에는 ‘임대인의 세금 체납 및 신용 상태는 고려하지 않은 결과이므로 주택도시보증공사에서 상세 상담 권고’ 문구를 넣어 10줄 정도로 부드럽게 설명해. **보증보험 가입여부 판단 사유를 설명할 때 점수총합은 절대 언급하지마.**"
                                              "ownerName" : "해당 부동산의 소유자 이름 (만약, 공동 소유자라면 더 지분이 높은 소유자의 이름)"
                                              "residentNum" : "주민등록번호 앞6자리(생년월일 부분)"
                                            }
                                            다음은 등기부등본 텍스트야:
                                            """,
                                    gptSecRequest.getInsuranceData()))); // 형변환 필요

        } else if (gptAnalysisRequest.getIsMonthlyRent() == 1) { // 월세 계약인 경우
            prompts.add(
                    Map.of(
                            "role",
                            "user",
                            "content",
                            String.format(
                                    """
                                            보증보험 가입이 가능한 다음 조건들을 보고 보증보험 가입 가능여부를 명확히 판단하고 그 판단의 이유를 다음을 기반으로 10줄 정도로 작성해서 insuranceDescription에 넣어줘:
                                            - insuranceData 값인 %d가 1인 경우만 보증보험 가입이 가능해.(왜냐하면 근저당, 시세, 보증금을 비교했을때 보증보험 가입 가능 여부를 충족해야지만 insuranceData가 1일 수 있기 때문)
                                            - 이 부동산이 아파트, 주거용 오피스텔, 연립·다세대주택, 단독·다중·다가구주택, 노인복지주택 중 하나여야만 해.
                                            
                                            이제 아래 등기부등본 택스트를 분석해서 각 조건을 엄격히 적용하여 판단하고, 다음 JSON 형식으로 결과를 응답:
                                            {
                                              "address":"표제부에 있는 이 부동산의 주소"
                                              "summary":"거래에 대한 세줄 요약"
                                              "safetyDescription":"두줄 요약 판단의 이유 10줄 정도를 공인중개사처럼 부드러운 상담톤으로 듣는 사람이 최대한 이해하기 쉽게 해줘"
                                              "insuranceDescription":"보증보험 가입 가능 여부를 조건에 맞게 명확히 판단하여 작성해. 가입 불가 시에는 반드시 ‘보증보험 가입이 불가합니다’라는 문장 포함해. 가입 가능 시에는 ‘임대인의 세금 체납 및 신용 상태는 고려하지 않은 결과이므로 주택도시보증공사에서 상세 상담 권고’ 문구를 넣어 10줄 정도로 부드럽게 설명해. **보증보험 가입여부 판단 사유를 설명할 때 점수총합은 절대 언급하지마.**"
                                              "ownerName" : "해당 부동산의 소유자 이름 (만약, 공동 소유자라면 더 지분이 높은 소유자의 이름)"
                                              "residentNum" : "주민등록번호 앞6자리(생년월일 부분)"
                                            }
                                            다음은 등기부등본 텍스트야:
                                            """,
                                    gptSecRequest.getInsuranceData())));
        }
        // gpt에게 추출된 택스트 입력
        prompts.add(Map.of("role", "user", "content", jsonText));

        return prompts;
    }

    // gpt-4o API 응답 파싱해서 데이터 가공하는 메소드
    public GptResponse parseGptResponse(String content) {
        try {
            return objectMapper.readValue(
                    content, GptResponse.class); // GptResponse 에서 지정한 응답 형식대로 자동 파싱해서 반환
        } catch (JsonProcessingException e) {
            throw new CustomException(GptErrorCode.GPT_RESPONSE_PARSING_FAILED);
        }
    }
}
