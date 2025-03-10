package org.chelonix.aws.appdemo;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Optional;
import org.chelonix.aws.appdemo.ports.out.LangDetector;
import org.chelonix.aws.appdemo.ports.out.TranslateService;
import org.chelonix.aws.appdemo.ports.out.TranslateServiceException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class TranslateHandlerTest {

  @InjectMock
  LangDetector langDetector;

  @InjectMock
  TranslateService translateService;

  private static final String SOURCE_TXT = "Hello from Translate API!";
  private static final String TARGET_TXT = "Bonjour de la Translate API !";
  private static final String SOURCE_LANG = "en";
  private static final String TARGET_LANG = "fr";
  private static final String INVALID_TARGET_LANG = "xx";

  @Test
  void should_translate_text() throws Exception {
    Mockito.when(langDetector.detectLang(anyString())).thenReturn(Optional.of(SOURCE_LANG));
    Mockito.when(translateService.translate(anyString(), eq(SOURCE_LANG), eq(TARGET_LANG)))
        .thenReturn(TARGET_TXT);

    // you test your lambdas by invoking on http://localhost:8081
    // this works in dev mode too

    given()
        .accept("application/json")
        .when()
        .get("/translate?q=%s&target=%s".formatted(SOURCE_TXT, TARGET_LANG))
        .then()
        .statusCode(200)
        .body(containsString("{\"translatedText\":\"%s\"}".formatted(TARGET_TXT)));
  }

  @Test
  void should_fail_unknown_lang() throws Exception {
    Mockito.when(langDetector.detectLang(anyString())).thenReturn(Optional.of(SOURCE_LANG));
    Mockito.when(translateService.translate(anyString(), anyString(), eq(INVALID_TARGET_LANG)))
        .thenThrow(new TranslateServiceException(
            "%s is not a valid target language".formatted(INVALID_TARGET_LANG)));

    given()
        .accept("application/json")
        .when()
        .get("/translate?q=%s&target=%s".formatted(SOURCE_TXT, INVALID_TARGET_LANG))
        .then()
        .statusCode(400)
        .body(containsString("\"title\":\"Bad Request\""))
        .body(containsString("\"detail\":\"%s is not a valid target language\"".formatted(INVALID_TARGET_LANG)));
  }

  @Test
  void should_fail_detect_lang() throws Exception {
    Mockito.when(langDetector.detectLang(anyString())).thenReturn(Optional.empty());
    Mockito.when(translateService.translate(anyString(), anyString(), eq(INVALID_TARGET_LANG)))
        .thenThrow(new TranslateServiceException(
            "%s is not a valid target language".formatted(INVALID_TARGET_LANG)));

    given()
        .accept("application/json")
        .when()
        .get("/translate?q=%s&target=%s".formatted(SOURCE_TXT, INVALID_TARGET_LANG))
        .then()
        .statusCode(400)
        .body(containsString("Could not detect input main language"));
    verify(translateService, never()).translate(anyString(), anyString(), anyString());
  }
}
