package org.chelonix.aws.appdemo.adapters.out;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.chelonix.aws.appdemo.ports.out.TranslateService;
import org.chelonix.aws.appdemo.ports.out.TranslateServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;
import software.amazon.awssdk.services.translate.model.TranslateTextResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AWSTranslateService implements TranslateService
{
  private TranslateClient client;

  @Inject
  public AWSTranslateService(@ConfigProperty(name = "aws.region") String awsRegion) {
    this.client = TranslateClient.builder().region(Region.of(awsRegion)).build();
  }

  @Override
  public String translate(String text, String sourceLang, String targetLang)
      throws TranslateServiceException {
    try {
      TranslateTextRequest request = TranslateTextRequest.builder()
          .text(text)
          .sourceLanguageCode(sourceLang)
          .targetLanguageCode(targetLang)
          .build();
      TranslateTextResponse result = client.translateText(request);
      return result.translatedText();
    } catch (Exception e) {
      throw new TranslateServiceException(e);
    }
  }

}
