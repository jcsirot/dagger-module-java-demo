package io.dagger.modules.ci;

import static io.dagger.client.Dagger.dag;

import io.dagger.client.CacheVolume;
import io.dagger.client.Client.AwsCliArguments;
import io.dagger.client.Container;
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

  /**
   * Build and test the application
   *
   * @param source    the project source directory. Default is the current directory
   * @param skipTests whether to skip tests or not
   */
  @Function
  public Container buildAndTest(@DefaultPath(".") Directory source, @Default("false") boolean skipTests) {
    CacheVolume mavenCache = dag().cacheVolume("m2");
    return dag()
        .container()
        .from("maven:3-eclipse-temurin-21")
        .withDirectory("/src", source)
        .withWorkdir("/src")
        .withMountedCache("/root/.m2/", mavenCache)
        .withExec(List.of("mvn", "-B", "clean", "package", "-DskipTests=%s".formatted(skipTests)));
  }

  private Secret ecrToken(Secret awsAccessKeyId, Secret awsSecretAccessKey, String region) {
    return dag().awsCli()
        .withRegion(region)
        .withStaticCredentials(awsAccessKeyId, awsSecretAccessKey)
        .ecr().getLoginPassword();
  }

  private Container buildImage(Directory source, Platform platform) {
    Container ctr = buildAndTest(source, true);
    return ctr.directory(".")
        .dockerBuild(new DockerBuildArguments()
            .withPlatform(platform)
            .withDockerfile("src/main/docker/Dockerfile.jvm"));
  }

  private String address(Directory source, String account, String region, String repoName)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    String hash = dag().gitInfo(source).commitHash().substring(0,8);
    return "%s.dkr.ecr.%s.amazonaws.com/%s:%s".formatted(account, region, repoName, hash);
  }

  /**
   * Builds the application and create a Docker image
   */
  // @Function
  public Container buildImage(@DefaultPath(".") Directory source) {
    return buildAndTest(source, true)
        .directory(".")
        .dockerBuild(new DockerBuildArguments()
            .withDockerfile("src/main/docker/Dockerfile.jvm"));
  }

  /**
   * Pushes a Docker image to ECR
   *
   * @param image the Docker image to push
   * @param address the ECR image address of the form <account_id>.dkr.ecr.<region>.amazonaws.com/<repository>:<tag>
   * @param token the ECR authentication token
   *
   * @return the image address
   */
  private String pushImage(Container image, String address, Secret token)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    return image
        .withRegistryAuth(address, "AWS", token)
        .publish(address);
  }

  /**
   * Builds and publishes the Docker image to ECR
   *
   * @param source             the source directory
   * @param awsAccessKeyId     the AWS access key ID
   * @param awsSecretAccessKey the AWS secret access key
   * @param accountId          the AWS account ID
   * @param region             the AWS region
   */
  @Function
  public String buildAndPushImage(@DefaultPath(".") Directory source, String repoName, Secret awsAccessKeyId, Secret awsSecretAccessKey, String accountId, @Default("eu-west-1") String region)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    Container image = buildImage(source, Platform.from("amd64"));
    String address = address(source, accountId, region, repoName);
    Secret token = ecrToken(awsAccessKeyId, awsSecretAccessKey, region);
    return pushImage(image, address, token);
  }

  private Container kubectl(String clusterName, Secret awsAccessKeyId, Secret awsSecretAccessKey, String region) {
    Container customCtr = dag().container().from("alpine")
      .withExec(List.of("apk", "add", "aws-cli", "kubectl"));
    List<String> cmd = List.of("eks", "update-kubeconfig", "--name", clusterName);
    return dag().awsCli(new AwsCliArguments().withContainer(customCtr))
        .withRegion(region)
        .withStaticCredentials(awsAccessKeyId, awsSecretAccessKey)
        .exec(cmd);
  }

  /**
   * Deploys the application to EKS
   *
   * @param source             the source directory
   * @param image              the image address to deploy
   * @param clusterName        the name of the EKS cluster
   * @param awsAccessKeyId     the AWS access key ID
   * @param awsSecretAccessKey the AWS secret access key
   * @param region             the AWS region
   */
  @Function
  public String deploy(@DefaultPath(".") Directory source, String image, String clusterName,
      Secret awsAccessKeyId, Secret awsSecretAccessKey, @Default("eu-west-1") String region)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    String appYaml = source.file("src/main/kube/app.yaml").contents().replace("${IMAGE_TAG}", image);
    return kubectl(clusterName, awsAccessKeyId, awsSecretAccessKey, region)
        .withNewFile("/tmp/app.yaml", appYaml)
        .withExec(List.of("kubectl", "apply", "-f", "/tmp/app.yaml"))
        .stdout();
  }

  /**
   * Returns the ingress address of the application
   *
   * @return the ingress address
   */
  @Function
  public String getIngress(String clusterName, Secret awsAccessKeyId, Secret awsSecretAccessKey,
      @Default("eu-west-1") String region)
      throws ExecutionException, DaggerQueryException, InterruptedException {
    String host = kubectl(clusterName, awsAccessKeyId, awsSecretAccessKey, region)
        .withExec(List.of("kubectl", "-n", "devoxxfr-dagger", "get", "ingress", "-o",
            "jsonpath={.items[0].status.loadBalancer.ingress[0].hostname}"))
        .stdout();
    return "http://%s".formatted(host);
  }
}
