package org.chelonix.aws.appdemo.ports.in;

import org.chelonix.aws.appdemo.model.TextTranslation;
import org.chelonix.aws.appdemo.ports.out.TranslateServiceException;

public interface TranslateTextUseCase {
  TextTranslation translate(String text, String targetLang, String sourceLang)
      throws TranslateServiceException;

  TextTranslation translate(String text, String targetLang) throws TranslateServiceException;
}
