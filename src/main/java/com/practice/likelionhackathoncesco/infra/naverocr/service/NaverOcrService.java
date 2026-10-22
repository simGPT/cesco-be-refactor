package com.practice.likelionhackathoncesco.infra.naverocr.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.practice.likelionhackathoncesco.domain.analysisreport.entity.AnalysisReport;
import com.practice.likelionhackathoncesco.domain.analysisreport.entity.ProcessingStatus;
import com.practice.likelionhackathoncesco.domain.analysisreport.exception.S3ErrorCode;
import com.practice.likelionhackathoncesco.domain.analysisreport.repository.AnalysisReportRepository;
import com.practice.likelionhackathoncesco.global.config.NaverOcrConfig;
import com.practice.likelionhackathoncesco.global.config.S3Config;
import com.practice.likelionhackathoncesco.global.exception.CustomException;
import com.practice.likelionhackathoncesco.infra.naverocr.dto.ImageDto;
import com.practice.likelionhackathoncesco.infra.naverocr.dto.request.OcrRequest;
import com.practice.likelionhackathoncesco.infra.naverocr.dto.response.OcrResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
@Setter
public class NaverOcrService {

  private final AmazonS3 amazonS3; // AWS SDK에서 제공하는 S3 클라이언트 객체
  private final S3Config s3Config; // 버킷 이름과 경로 등 설정 정보
  private final RestTemplate restTemplate; // RestAPI 호출용
  private final AnalysisReportRepository analysisReportRepository;
  private final NaverOcrConfig naverOcrConfig; // API InvokeUrl, seceretKey
  private final OcrResponseParser ocrResponseParser;

  // ocr로 텍스트 추출 -> 분석 리포트 등기부등본!!!!!!!!!!
  public OcrResponse extractText(Long reportId) {

    AnalysisReport analysisReport =
        analysisReportRepository
            .findById(reportId)
            .orElseThrow(() -> new CustomException(S3ErrorCode.FILE_NOT_FOUND));

    try {
      log.info("OCR 처리 시작: s3key={}", analysisReport.getS3Key());

      // s3에서 파일 다운로드
      // S3ObjectInputStream은 네트워크 연결을 유지하는 스트림이기 때문에 사용 후 닫아야 함(try-with-resource구문)
      try {

        // OCR API 요청 생성
        OcrRequest requestDto =
            createOcrRequest(analysisReport.getS3Key(), analysisReport.getFileName());

        // DB에 진행 상태 필드 업데이트
        analysisReport.updateProcessingStatus(ProcessingStatus.OCR_PROCESSING);
        analysisReportRepository.save(analysisReport);

        // Ocr API 호출
        OcrResponse ocrResult = callOcrApi(requestDto);

        log.info("OCR 처리 완료: reportId={}", reportId);

        // DB에 진행 상태 필드 업데이트
        analysisReport.updateProcessingStatus(ProcessingStatus.OCR_COMPLETED);
        analysisReportRepository.save(analysisReport);

        return ocrResult;

      } catch (CustomException e) {
        throw new CustomException(S3ErrorCode.FILE_DOWNLOAD_FAIL);
      }

    } catch (CustomException e) { // 추후에 ocrErrorCode 작성 후 예외 던지기
      log.error("OCR 처리 중 예상치 못한 오류: s3key={}", analysisReport.getS3Key(), e);
      return OcrResponse.builder()
          .processingStatus(ProcessingStatus.FAILED) // ocr 실패
          .build();
    } catch (IOException e) { // callOcrApi()에서 IOException을 던지고 있기 때문에 받아서 다시 던져야 함
      throw new RuntimeException(e);
    }
  }

  // pdf 전용 ocr 요청 생성
  protected OcrRequest createOcrRequest(String s3key, String fileName) {

    // s3 객체 url로 요청을 보냄(presignedUrl 방식으로 변경)
    Date expiration = new Date(System.currentTimeMillis() + 1000 * 60 * 10);
    GeneratePresignedUrlRequest presignedUrlRequest =
        new GeneratePresignedUrlRequest(s3Config.getBucket(), s3key)
            .withMethod(com.amazonaws.HttpMethod.GET)
            .withExpiration(expiration);
    String s3Url = amazonS3.generatePresignedUrl(presignedUrlRequest).toString();

    log.info("생성된 S3 URL: {}", s3Url);
    log.info("버킷명: {}, S3 키: {}", s3Config.getBucket(), s3key);

    // 공식 문서 기준 이미지 요청 방식
    ImageDto pdfImage = ImageDto.builder().format("pdf").name(fileName).url(s3Url).build();

    // 공식 문서 기준 요청 방식
    return OcrRequest.builder()
        .version("V2")
        .requestId("pdf-" + System.currentTimeMillis()) // 임의의 API 호출 UUID
        .timestamp(System.currentTimeMillis()) // 임의의 API 호출 시각
        .lang("ko") // OCR 인식 요청 언어 정보
        .enableTableDetection(true) // 표 형태 제공 (우리는 등기부등본이기 때문에 표 형태로 제공받아야 보기 편할 듯)
        .images(List.of(pdfImage)) // JSON Array로 작성, 호출당 1개의 이미지 Array 작성 가능, 이미지 크기: 최대 50MB
        .build();
  }

  // OCR API 호출하여 파싱된 데이터 반환
  protected OcrResponse callOcrApi(OcrRequest request) throws IOException {
    try {
      // HTTP 헤더 설정 (공식 문서 -> X-OCR-SECRET / Content-Type 2가지 필드 필요
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-OCR-SECRET", naverOcrConfig.getSecretKey());

      // API 요청 엔티티 생성 (OcrRequest DTO와 헤더를 함께 포장해서 전송)
      HttpEntity<OcrRequest> requestEntity = new HttpEntity<>(request, headers);

      log.info("Naver OCR API 호출 시작: requestId={}", request.getRequestId());

      // 이 요청방식 대로 요청을 보내면 응답을 받을 수 있음 -> 응답을 생성
      ResponseEntity<String> response =
          restTemplate.exchange(
              naverOcrConfig.getInvokeUrl(), // api 엔드포인트
              HttpMethod.POST, // http 메서드
              requestEntity, // 헤더와 생성한 요청
              String.class // 응답 타입
              );

      // 응답에서 텍스트 파싱 (응답을 정리한다고 생각) 하여 반환
      return ocrResponseParser.parseResponse(response);

    } catch (Exception e) {
      log.error("OCR API 호출 실패", e);
      throw new IOException("OCR API 호출 실패", e);
    }
  }
}
