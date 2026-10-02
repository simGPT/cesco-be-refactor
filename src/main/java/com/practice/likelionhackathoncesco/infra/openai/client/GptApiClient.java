package com.practice.likelionhackathoncesco.infra.openai.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.likelionhackathoncesco.global.exception.CustomException;
import com.practice.likelionhackathoncesco.infra.openai.exception.GptErrorCode;
import com.practice.likelionhackathoncesco.infra.openai.global.config.GptConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component // @Service, @Repository, @Controller 등으로 구분하기 애매한 일반 Bean이라서 @Component 사용
@RequiredArgsConstructor
public class GptApiClient {

  private final GptConfig gptConfig;
  private final ObjectMapper objectMapper; // for 역직렬화(openai api json 응답 -> java 객체)
  private final RestTemplate restTemplate = new RestTemplate(); // openai api에 post 요청을 보내기 위함

  public String callGptAPI(List<Map<String, String>> prompts, String reportId) {

    // requestBody
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("model", gptConfig.getModel());
    requestBody.put("messages", prompts);
    requestBody.put("temperature", 0.7); // gpt의 답변 창의성 정도 -> 0.7이 중간정도

    HttpHeaders headers = new HttpHeaders(); // 요청에 붙은 http 헤더로 API Key, 데이터타입 포함
    headers.setContentType(MediaType.APPLICATION_JSON); // http 헤더에 요청이 JSON 형식이라고 지정 추가
    headers.setBearerAuth(gptConfig.getSecretKey()); // http 헤더에 api key 추가

    // 요청 객체 생성 (헤더와 바디 포함)
    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

    try {
      log.info("[GptApiClient] GPT API 요청 시도 : reportId={}", reportId);
      // POST 요청
      ResponseEntity<Map> response =
          restTemplate.postForEntity(gptConfig.getUrl(), requestEntity, Map.class);

      if (response.getStatusCode() != HttpStatus.OK) { // 응답코드 200일때만 응답 꺼내기
        log.error(
            "[GptApiClient] GPT API 응답 실패 : reportId={}, httpStatus={}",
            reportId,
            response.getStatusCode());
        throw new CustomException(GptErrorCode.GPT_API_CALL_FAILED);
      }

      Map<String, Object> responseBody = response.getBody(); // requestBody 꺼내기
      // gpt 응답은 항상 choices 라는 배열 갖고 있음 -> choices 변수에 따로 저장 필요

      // 이부분 경고가 계속 띀!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
      List<Map<String, Object>> choices;
      choices =
          objectMapper.convertValue(
              responseBody.get("choices"), new TypeReference<List<Map<String, Object>>>() {});

      if (choices == null || choices.isEmpty()) {
        log.warn("[GptApiClient] GPT API 응답 성공했으나 choices 변수 null : reportId={}", reportId);
        throw new CustomException(GptErrorCode.GPT_EMPTY_RESPONSE);
      }

      // message 변수에 gpt 응답이 담김
      Map<String, Object> message =
          objectMapper.convertValue(
              choices.get(0).get("message"), new TypeReference<Map<String, Object>>() {});
      String content =
          ((String) message.get("content"))
              .replaceAll("```json", "")
              .replaceAll("```", "")
              .trim(); // message안에는 role과 content가 있는데 이중 content가 진짜 답변!

      log.info("[GptApiClient] 응답 성공 : reportId={}, content={}", reportId, content);
      return content; // 이게 찐 gpt 응답 텍스트!

    } catch (HttpClientErrorException e) {
      log.error(
          "[GptApiClient] GPT API 클라이언트 오류: reportId={}, {}",
          reportId,
          e.getResponseBodyAsString());
      throw new CustomException(GptErrorCode.GPT_INVALID_PROMPT);

    } catch (ResourceAccessException e) {
      log.error("[GptApiClient] GPT API 타임아웃 또는 접근 실패: reportId={}, {}", reportId, e.getMessage());
      throw new CustomException(GptErrorCode.GPT_TIMEOUT);

    } catch (Exception e) {
      log.error("[GptApiClient] GPT API 호출 중 예외 발생: reportId={}, {}", reportId, e.getMessage());
      throw new CustomException(GptErrorCode.GPT_API_CALL_FAILED);
    }
  }
}
