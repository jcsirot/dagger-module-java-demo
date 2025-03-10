package org.chelonix.aws.appdemo.model;

public record TextTranslation(String sourceText,
                              String sourceLang,
                              String targetLang,
                              String translatedText) {

}
