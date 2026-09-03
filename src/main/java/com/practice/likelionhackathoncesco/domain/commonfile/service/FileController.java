package com.practice.likelionhackathoncesco.domain.commonfile.service;

import com.practice.likelionhackathoncesco.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "S3", description = "S3 업로드 테스트용 API")
public class FileController {
  private final FileService fileService;

  @Operation(summary = "S3 업로드 테스트용 API", description = "S3 계정 및 버킷 설정 변경 후 테스트를 위한 API")
  @PostMapping(
      value = "/s3/upload",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE) // 파일 업로드하는 형식으로 설정
  public ResponseEntity<BaseResponse<String>> upload(
      @Parameter(description = "업로드 할 파일") @RequestParam("uploadFile") MultipartFile uploadFile) {

    String keyName = fileService.s3UploadTest("test", uploadFile); // test 경로는 문자열로 전달

    return ResponseEntity.ok(BaseResponse.success("S3 파일 업로드 테스트 완료", keyName));
  }
}
