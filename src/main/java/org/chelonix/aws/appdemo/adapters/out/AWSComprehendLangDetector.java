package org.chelonix.aws.appdemo.adapters.out;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.chelonix.aws.appdemo.ports.out.LangDetector;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.DetectDominantLanguageRequest;
import software.amazon.awssdk.services.comprehend.model.DetectDominantLanguageResponse;

@ApplicationScoped
public class AWSComprehendLangDetector implements LangDetector {

  private ComprehendClient comprehendClient;

  public AWSComprehendLangDetector(@ConfigProperty(name = "aws.region") String awsRegion) {
    this.comprehendClient = ComprehendClient.builder().region(Region.of(awsRegion)).build();
  }

  @Override
  public Optional<String> detectLang(String text) {
    DetectDominantLanguageResponse detectDominantLanguageResponse = comprehendClient
        .detectDominantLanguage(
            DetectDominantLanguageRequest.builder().text(text).build());
    if (detectDominantLanguageResponse.hasLanguages()) {
      return Optional.of(detectDominantLanguageResponse.languages().get(0).languageCode());
    }
    return Optional.empty();
  }
}
