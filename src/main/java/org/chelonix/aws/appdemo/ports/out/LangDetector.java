package org.chelonix.aws.appdemo.ports.out;

import java.util.Optional;

public interface LangDetector {
  Optional<String> detectLang(String text);
}
