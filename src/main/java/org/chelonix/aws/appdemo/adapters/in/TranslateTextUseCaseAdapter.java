package org.chelonix.aws.appdemo.adapters.in;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.chelonix.aws.appdemo.model.TextTranslation;
import org.chelonix.aws.appdemo.ports.in.TranslateTextUseCase;
import org.chelonix.aws.appdemo.ports.out.LangDetector;
import org.chelonix.aws.appdemo.ports.out.TranslateService;
import org.chelonix.aws.appdemo.ports.out.TranslateServiceException;

@ApplicationScoped
public class TranslateTextUseCaseAdapter implements TranslateTextUseCase {

  private final TranslateService translateService;
  private final LangDetector langDetector;

  @Inject
  public TranslateTextUseCaseAdapter(TranslateService translateService, LangDetector langDetector) {
    this.translateService = translateService;
    this.langDetector = langDetector;
  }

  @Override
  public TextTranslation translate(String text, String targetLang, String sourceLang)
      throws TranslateServiceException {
    String translatedText = translateService.translate(text, sourceLang, targetLang);
    return new TextTranslation(text, sourceLang, targetLang, translatedText);
  }

  @Override
  public TextTranslation translate(String text, String targetLang) throws TranslateServiceException {
    String sourceLang = langDetector.detectLang(text)
        .orElseThrow(() -> new TranslateServiceException("Could not detect input main language"));
    return translate(text, targetLang, sourceLang);
  }
}
