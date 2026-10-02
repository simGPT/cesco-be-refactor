package com.practice.likelionhackathoncesco.infra.openai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.likelionhackathoncesco.domain.fraudreport.dto.response.FakerResponse;
import com.practice.likelionhackathoncesco.domain.fraudreport.entity.Faker;
import com.practice.likelionhackathoncesco.domain.fraudreport.entity.FraudRegisterReport;
import com.practice.likelionhackathoncesco.domain.fraudreport.entity.ReportStatus;
import com.practice.likelionhackathoncesco.domain.fraudreport.exception.FraudReport_Error_Code;
import com.practice.likelionhackathoncesco.domain.fraudreport.repository.FakerRepository;
import com.practice.likelionhackathoncesco.domain.fraudreport.repository.FraudRegisterReportRepository;
import com.practice.likelionhackathoncesco.global.exception.CustomException;
import com.practice.likelionhackathoncesco.infra.naverocr.service.FraudOcrService;
import com.practice.likelionhackathoncesco.infra.openai.dto.response.GptComplaintResponse;
import com.practice.likelionhackathoncesco.infra.openai.dto.response.GptOwnerListResponse;
import com.practice.likelionhackathoncesco.infra.openai.exception.GptErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GptComplaintService {

    // gpt API 에 신고용 등기부등본 추출 택스트 전달해서 신고당한 임대인 정보 추출
    private final FraudOcrService fraudOcrService;
    private final ObjectMapper objectMapper;
    private final FakerRepository fakerRepository;
    private final FraudRegisterReportRepository fraudRegisterReportRepository;

    // 신고 당한 임대인 정보 DB에 저장하는 메소드
    @Transactional
    public List<FakerResponse> saveFakerInfo(
            List<GptComplaintResponse> responseList, Long fraudRegisterReportId) {

        FraudRegisterReport fraudRegisterReport =
                fraudRegisterReportRepository
                        .findById(fraudRegisterReportId)
                        .orElseThrow(() -> new CustomException(FraudReport_Error_Code.FRAUD_REPORT_NOT_FOUND));

        // 리스트로 반환되는 임대인 정보를 리스트 인덱스 단위로 객체 생성
        List<Faker> fakerList =
                responseList.stream()
                        .map(
                                r ->
                                        Faker.builder()
                                                .fakerName(r.getFakerName())
                                                .residentNum(r.getResidentNum())
                                                .fraudRegisterReport(fraudRegisterReport)
                                                .build())
                        .toList();

        // DB 저장
        fakerRepository.saveAll(fakerList);

        // 신고 상태 업데이트
        fraudRegisterReport.updateReportStatus(ReportStatus.REPORTCOMPLETED);

        // 저장된 엔티티 DTO로 변환 후 반환
        return fakerList.stream()
                .map(
                        f ->
                                new FakerResponse(
                                        f.getFakerName(),
                                        f.getResidentNum(),
                                        f.getFraudRegisterReport().getReportStatus()))
                .toList();
    }

    // 신고용 등기부등본에서 임대인 정보 요청을 위해 gpt-4o용 프롬프트 생성
    public List<Map<String, String>> createGetFakerPrompt(Long fraudRegisterReportId)
            throws JsonProcessingException {

        // 신고 등기부등본 갑구 파싱 택스트 바로 가져오기
        List<String> text = fraudOcrService.gapguExtractText(fraudRegisterReportId);
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonText = objectMapper.writeValueAsString(text);

        List<Map<String, String>> prompts = new ArrayList<>();

        // gpt에게 행동지침을 주는 역할의 프롬프트
        prompts.add(
                Map.of("role", "system", "content", "너는 한국 등기부등본 문서를 분석해서 소유자의 정보를 추출하는 전문 AI 어시스턴트야."));

        // gpt에게 사용자가 질문하거나 지시하는 메시지 -> JSON 형식으로 응답해달라는 것 반드시 명시
        prompts.add(
                Map.of(
                        "role",
                        "user",
                        "content",
                        String.format(
                                """
                                        아래는 ocr로 추출한 등기부등본 갑구에서 추출한 택스트야.
                                        여기서 소유자의 이름과 주민등록번호 앞6자리(생년월일 부분)만 추출해서 JSON 형식으로 반환해줘.
                                        소유자가 한명 이상일때는 배열로 모두 알려줘.
                                        
                                        OCR 결과:
                                        %s
                                        
                                        너는 오직 아래 JSON형식으로만 응답해야해. 그리고 절대 형식을 벗어나지 말아야해.
                                        {
                                          "faker": [
                                            {"fakerName": "이름", "residentNum": "주민등록번호 앞6자리"}
                                          ]
                                        }
                                        
                                        반환 형식 예시:
                                        {
                                          "faker" : [
                                            { "fakerName" : "홍길동", "residentNum" : "660101" },
                                            { "fakerName" : "신짱구", "residentNum" : "701106" }
                                          ]
                                        }
                                        """,
                                String.join("\n", jsonText))));

        return prompts;
    }

    // gpt-4o API 응답 파싱 메소드
    public GptOwnerListResponse parseGptOwnerListResponse(String content) {
        try {
            return objectMapper.readValue(content, GptOwnerListResponse.class);
        } catch (JsonProcessingException e) {
            throw new CustomException(GptErrorCode.GPT_RESPONSE_PARSING_FAILED);
        }
    }

    // 배열 형태의 gpt-4o 응답 리스트로 반환
    public List<GptComplaintResponse> parseGptComplaintResponseList(String content) {
        GptOwnerListResponse gptOwnerListResponse = parseGptOwnerListResponse(content);
        return gptOwnerListResponse.getFaker().stream()
                .map(f -> new GptComplaintResponse(f.getFakerName(), f.getResidentNum()))
                .toList();
    }
}
