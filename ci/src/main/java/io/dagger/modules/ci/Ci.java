package io.dagger.modules.ci;

import static io.dagger.client.Dagger.dag;

import io.dagger.client.AwsCli;
import io.dagger.client.CacheVolume;
import io.dagger.client.Client.AwsCliArguments;
import io.dagger.client.Container;
import io.dagger.client.Container.PublishArguments;
import io.dagger.client.DaggerQueryException;
import io.dagger.client.Directory;
import io.dagger.client.Directory.DockerBuildArguments;
import io.dagger.client.Platform;
import io.dagger.client.Secret;
import io.dagger.module.annotation.Default;
import io.dagger.module.annotation.DefaultPath;
import io.dagger.module.annotation.Function;
import io.dagger.module.annotation.Object;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ci main object */
@Object
public class Ci {

  static final Logger LOG = LoggerFactory.getLogger(Ci.class);

  private static final List<String> ARCHS = List.of("amd64", "arm64");

  /** Build and test the application */
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

  /**
   * Build a list of Docker images for multiple architectures
   * @param source the source directory
   */
  private List<Container> buildImageMultiarch(Directory source, List<String> variants) {
    List<Container> images = variants.stream().map(platform -> {
      try {
        LOG.info("Building image for {}", platform);
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
   * Publishes the Docker image to ECR
   *
   * @param source             the source directory
   * @param awsAccessKeyId     the AWS access key ID
   * @param awsSecretAccessKey the AWS secret access key
   * @param region             the AWS region
   */
  @Function
  public String publish(@DefaultPath(".") Directory source, Secret awsAccessKeyId,
      Secret awsSecretAccessKey, @Default("eu-west-1") String region)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    AwsCli awsCli = dag().awsCli()
        .withRegion(region)
        .withStaticCredentials(awsAccessKeyId, awsSecretAccessKey);
    Secret token = awsCli.ecr().getLoginPassword();
    String accountId = awsCli.sts().getCallerIdentity().account();
    String address = "%s.dkr.ecr.%s.amazonaws.com/parisjug-dagger-demo/translate-api:%s"
        .formatted(accountId, region, dag().gitInfo(source).commitHash().substring(0, 8));
    dag().container()
        .withRegistryAuth(address, "AWS", token)
        .publish(address, new PublishArguments()
            .withPlatformVariants(buildImageMultiarch(source, ARCHS)));
    return address;
  }

  /**
   * Deploys the application to EKS
   *
   * @param source the source directory
   * @param image the image address to deploy
   * @param clusterName the name of the EKS cluster
   * @param awsAccessKeyId the AWS access key ID
   * @param awsSecretAccessKey the AWS secret access key
   * @param region the AWS region
   */
  @Function
  public String deploy(@DefaultPath(".") Directory source, String image, String clusterName,
      Secret awsAccessKeyId, Secret awsSecretAccessKey, @Default("eu-west-1") String region)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    Container deployerCtr = dag().container().from("alpine")
        .withExec(List.of("apk", "add", "aws-cli", "kubectl"));
    String appYaml = source.file("src/main/kube/app.yaml").contents()
        .replace("${IMAGE_TAG}", image);
    return dag().awsCli(new AwsCliArguments().withContainer(deployerCtr))
        .withRegion(region)
        .withStaticCredentials(awsAccessKeyId, awsSecretAccessKey)
        .exec(List.of("eks", "update-kubeconfig", "--name", clusterName))
        .withEnvVariable("IMAGE_TAG", image)
        .withNewFile("/tmp/app.yaml", appYaml)
        .withExec(List.of("kubectl", "apply", "-f", "/tmp/app.yaml"))
        .stdout();
  }

  /** Build a ready-to-use development environment */
  private Container buildEnv(Directory source) {
    CacheVolume mavenCache = dag().cacheVolume("m2");
    return dag()
        .container()
        .from("maven:3-eclipse-temurin-21")
        .withDirectory("/src", source)
        .withMountedCache(".m2/", mavenCache)
        .withWorkdir("/src");
  }
}
