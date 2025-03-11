package io.dagger.modules.ci;

import io.dagger.client.AwsCli;
import io.dagger.client.CacheVolume;
import io.dagger.client.Client.AwsCliArguments;
import io.dagger.client.Container;
import io.dagger.client.Container.PublishArguments;
import io.dagger.client.Container.WithExecArguments;
import io.dagger.client.DaggerQueryException;
import io.dagger.client.Directory;
import io.dagger.client.Directory.DockerBuildArguments;
import io.dagger.client.Platform;
import io.dagger.client.Secret;
import io.dagger.client.Service;
import io.dagger.module.AbstractModule;
import io.dagger.module.annotation.Default;
import io.dagger.module.annotation.DefaultPath;
import io.dagger.module.annotation.Function;
import io.dagger.module.annotation.Object;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Ci main object */
@Object
public class Ci extends AbstractModule {

  /**
   * Builds the application and optionally run the tests
   *
   * @param source    the source directory
   * @param skipTests whether to skip the tests
   */
  @Function
  public Container build(@DefaultPath(".") Directory source, @Default("false") boolean skipTests)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    return buildEnv(source)
        .withExec(List.of("mvn", "-B", "clean", "package", "-DskipTests=%s".formatted(skipTests)));
  }

  /**
   * Builds the application and create a Docker image
   */
  @Function
  public Container buildImage(@DefaultPath(".") Directory source)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    Container ctr = build(source, true);
    return ctr.directory(".")
        .dockerBuild(new DockerBuildArguments()
            .withDockerfile("src/main/docker/Dockerfile.jvm"));
  }

  private List<Container> buildImageMultiarch(Directory source) {
    List<String> variants = List.of("amd64", "arm64");
    List<Container> images = variants.stream().map(platform -> {
      try {
        return buildImage(source, platform);
      } catch (ExecutionException | DaggerQueryException | InterruptedException e) {
        throw new RuntimeException(e);
      }
    }).toList();
    return images;
  };

  private Container buildImage(Directory source, String platform)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    Container ctr = build(source, true);
    return ctr.directory(".")
        .dockerBuild(new DockerBuildArguments()
            .withPlatform(Platform.from(platform))
            .withDockerfile("src/main/docker/Dockerfile.jvm"));
  }

  /**
   * Runs the application as a service
   */
  @Function
  public Service run(@DefaultPath(".") Directory source, @Default("8080") int port,
      Secret awsAccessKeyId, Secret awsSecretAccessKey)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    Container ctr = buildImage(source)
        .withSecretVariable("AWS_ACCESS_KEY_ID", awsAccessKeyId)
        .withSecretVariable("AWS_SECRET_ACCESS_KEY", awsSecretAccessKey);
    return ctr.asService();
  }

  /**
   * Publishes the Docker image to ECR
   */
  @Function
  public String publish(@DefaultPath(".") Directory source, Secret awsAccessKeyId, Secret awsSecretAccessKey)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    AwsCli awsCli = dag.awsCli().withRegion("eu-west-1").withStaticCredentials(awsAccessKeyId, awsSecretAccessKey);
    Secret token = awsCli.ecr().getLoginPassword();
    String accountId = awsCli.sts().getCallerIdentity().account();
    String address = "%s.dkr.ecr.eu-west-1.amazonaws.com/parisjug-dagger-demo/translate-api:%s"
        .formatted(accountId, dag.gitInfo(source).commitHash().substring(0, 8));
    dag.container()
        .withRegistryAuth(address, "AWS", token)
        .publish(address, new PublishArguments().withPlatformVariants(buildImageMultiarch(source)));
    return address;
  }

  /**
   * Deploys the application to EKS
   */
  @Function
  public String deploy(@DefaultPath(".") Directory source, String image, Secret awsAccessKeyId, Secret awsSecretAccessKey)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    Container deployerCtr = dag.container().from("alpine")
        .withExec(List.of("apk", "add", "aws-cli", "kubectl"));
    String appYaml = source.file("src/main/kube/app.yaml").contents().replace("${IMAGE_TAG}", image);
    return dag.awsCli(new AwsCliArguments().withContainer(deployerCtr))
        .withRegion("eu-west-1")
        .withStaticCredentials(awsAccessKeyId, awsSecretAccessKey)
        .exec(List.of("eks", "update-kubeconfig", "--name", "confused-classical-sheepdog"))
        .withEnvVariable("IMAGE_TAG", image)
        .withNewFile("/tmp/app.yaml", appYaml)
        .withExec(List.of("kubectl", "apply", "-f", "/tmp/app.yaml"))
        .stdout();
  }

  /** Build a ready-to-use development environment */
  private Container buildEnv(Directory source) {
    CacheVolume mavenCache = dag.cacheVolume("m2");
    return dag
        .container()
        .from("maven:3-eclipse-temurin-21")
        .withDirectory("/src", source)
        .withMountedCache(".m2/", mavenCache)
        .withWorkdir("/src");
  }
}
