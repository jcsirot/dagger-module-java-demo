package org.chelonix.aws.appdemo.resource;

import org.chelonix.aws.appdemo.model.TextTranslation;

public record TextTranslationResource(String translatedText) {

  public static TextTranslationResource from(TextTranslation translation) {
    return new TextTranslationResource(translation.translatedText());
  }
}
