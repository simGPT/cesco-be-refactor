package com.practice.likelionhackathoncesco.domain.analysisreport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.practice.likelionhackathoncesco.domain.analysisreport.dto.response.AnalysisReportResponse;
import com.practice.likelionhackathoncesco.domain.analysisreport.entity.AnalysisReport;
import com.practice.likelionhackathoncesco.domain.analysisreport.entity.PathName;
import com.practice.likelionhackathoncesco.domain.analysisreport.entity.ProcessingStatus;
import com.practice.likelionhackathoncesco.domain.analysisreport.repository.AnalysisReportRepository;
import com.practice.likelionhackathoncesco.domain.commonfile.service.FileService;
import com.practice.likelionhackathoncesco.infra.openai.client.GptApiClient;
import com.practice.likelionhackathoncesco.infra.openai.dto.request.GptAnalysisRequest;
import com.practice.likelionhackathoncesco.infra.openai.dto.request.GptSecRequest;
import com.practice.likelionhackathoncesco.infra.openai.dto.response.GptDeptResponse;
import com.practice.likelionhackathoncesco.infra.openai.dto.response.GptResponse;
import com.practice.likelionhackathoncesco.infra.openai.service.GptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisFlowService {

    private final GptService gptService;
    private final AnalysisReportService analysisReportService;
    private final FileService fileService;
    private final AnalysisReportRepository analysisReportRepository;
    private final GptApiClient gptApiClient;

    // 분석 리포트를 위한 등기부등본 S3 업로드 + DB 저장
    @Transactional
    public AnalysisReport uploadDocuments(PathName pathName, MultipartFile file) {

        AnalysisReport savedReport =
                fileService.uploadFile(
                        pathName,
                        file,
                        () -> AnalysisReport.builder().processingStatus(ProcessingStatus.UPLOADED).build(),
                        analysisReportRepository,
                        null);

        return savedReport;
    }

    public AnalysisReportResponse processAnalysisReport(
            Long reportId, GptAnalysisRequest gptAnalysisRequest) {

        // 프롬프트 제작
        List<Map<String, String>> promptsForDept; // 근저당 프롬프트
        List<Map<String, String>> prompts; // 분석레포트 프롬프트

        // 지피티 작업 중으로 상태 업데이트 for 프론트
        analysisReportService.updateProcessingStatus(reportId, ProcessingStatus.GPT_PROCESSING);

        // 근저당 총액을 알아내기 위한 프롬프트
        try {
            promptsForDept = gptService.createPromptForDept(gptAnalysisRequest, reportId);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            promptsForDept = new ArrayList<>();
        }

        // gpt-4o api 호출로 근저당 총액 응답 받기
        String contentForDept = gptApiClient.callGptAPI(promptsForDept, String.valueOf(reportId));

        // 근저당 총액 gpt 응답을 파싱하는 메소드
        GptDeptResponse gptDeptResponse = gptService.parseDeptResponse(contentForDept);
        log.info(
                "[AnalysisFlowService] gpt api에게 dept, dangerNum 응답 받은 후 파싱 완료: dept={}, dangerNum={}",
                gptDeptResponse.getDept(),
                gptDeptResponse.getDangerNum());

        // gpt 에게 전달할 값 세개
        GptSecRequest gptSecRequest =
                analysisReportService.getGptSecRequest(gptAnalysisRequest, gptDeptResponse, reportId);

        System.out.println(gptSecRequest.getSafetyScoreStatus());

        // gpt에게 필요한 정보 추가해서 최종 프롬프트 생성
        try {
            prompts = gptService.createPrompt(gptAnalysisRequest, gptSecRequest, reportId);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            prompts = new ArrayList<>();
        }

        // gpt-4o api 호출
        String content = gptApiClient.callGptAPI(prompts, String.valueOf(reportId));

        // 응답 파싱
        GptResponse gptResponse = gptService.parseGptResponse(content);

        // 분석 리포트 분석 후 DB 업데이트
        AnalysisReportResponse analysisReportResponse =
                analysisReportService.updateAnalysisReport(gptResponse, gptSecRequest, reportId);

        return analysisReportResponse;
    }
}
