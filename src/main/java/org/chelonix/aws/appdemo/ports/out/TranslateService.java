package org.chelonix.aws.appdemo.ports.out;

public interface TranslateService {
  String translate(String text, String sourceLang, String targetLang)
      throws TranslateServiceException;
}
