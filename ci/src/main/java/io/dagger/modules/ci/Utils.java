package io.dagger.modules.ci;

import static io.dagger.client.Dagger.dag;

import io.dagger.client.AwsCli;
import io.dagger.client.Client.AwsCliArguments;
import io.dagger.client.Container;
import io.dagger.client.Container.WithExecArguments;
import io.dagger.client.DaggerQueryException;
import io.dagger.client.Secret;
import java.util.List;
import java.util.concurrent.ExecutionException;

final class Utils {

  static AwsCli aws(String region, Secret awsAccessKeyId, Secret awsSecretAccessKey) {
    return dag().awsCli()
        .withRegion(region)
        .withStaticCredentials(awsAccessKeyId, awsSecretAccessKey);
  }

  static Container kubectl(String clusterName, String region, Secret awsAccessKeyId, Secret awsSecretAccessKey) {
    Container customContainer = dag().container().from("alpine")
        .withExec(List.of("apk", "add", "aws-cli", "kubectl"));
    return dag().awsCli(new AwsCliArguments().withContainer(customContainer))
        .withRegion(region)
        .withStaticCredentials(awsAccessKeyId, awsSecretAccessKey)
        .exec(List.of("eks", "update-kubeconfig", "--name", clusterName));
  }

  static String envsubst(String content, String... substitutions)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    Container container = dag().container().from("alpine")
        .withExec(List.of("apk", "add", "envsubst"));
    for (int i = 0; i < substitutions.length; i += 2) {
      container = container.withEnvVariable(substitutions[i], substitutions[i + 1]);
    }
    return container
        .withExec(List.of("envsubst"), new WithExecArguments().withStdin(content))
        .stdout();
  }
}
