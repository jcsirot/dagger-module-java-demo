package io.dagger.modules.ci;

import static io.dagger.client.Dagger.dag;

import io.dagger.client.AwsCli;
import io.dagger.client.Client.AwsCliArguments;
import io.dagger.client.Container;
import io.dagger.client.Secret;

final class Utils {

  static AwsCli aws(String region, Secret awsAccessKeyId, Secret awsSecretAccessKey) {
    return dag().awsCli()
        .withRegion(region)
        .withStaticCredentials(awsAccessKeyId, awsSecretAccessKey);
  }

  static AwsCli aws(Container customContainer, String region, Secret awsAccessKeyId, Secret awsSecretAccessKey) {
    return dag().awsCli(new AwsCliArguments().withContainer(customContainer))
        .withRegion(region)
        .withStaticCredentials(awsAccessKeyId, awsSecretAccessKey);
  }

}
