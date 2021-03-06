package io.snyk.jenkins.tools;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import io.snyk.jenkins.SnykStepBuilder.SnykStepBuilderDescriptor;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import static java.lang.String.format;

public class SnykInstallation extends ToolInstallation implements EnvironmentSpecific<SnykInstallation>, NodeSpecific<SnykInstallation> {

  @DataBoundConstructor
  public SnykInstallation(@Nonnull String name, @Nonnull String home, List<? extends ToolProperty<?>> properties) {
    super(name, home, properties);
  }

  @Override
  public void buildEnvVars(EnvVars env) {
    String root = getHome();
    if (root != null) {
      env.put("PATH+SNYK_HOME", new File(root, "node_modules/.bin").toString());
    }
  }

  @Override
  public SnykInstallation forEnvironment(EnvVars environment) {
    return new SnykInstallation(getName(), environment.expand(getHome()), getProperties().toList());
  }

  @Override
  public SnykInstallation forNode(@Nonnull Node node, TaskListener log) throws IOException, InterruptedException {
    return new SnykInstallation(getName(), translateFor(node, log), getProperties().toList());
  }

  public String getSnykExecutable(@Nonnull Launcher launcher, Platform platform) throws IOException, InterruptedException {
    return launcher.getChannel().call(new MasterToSlaveCallable<String, IOException>() {
      @Override
      public String call() throws IOException {
        return resolveExecutable("snyk", platform);
      }
    });
  }

  public String getReportExecutable(@Nonnull Launcher launcher, Platform platform) throws IOException, InterruptedException {
    return launcher.getChannel().call(new MasterToSlaveCallable<String, IOException>() {
      @Override
      public String call() throws IOException {
        return resolveExecutable("snyk-to-html", platform);
      }
    });
  }

  private String resolveExecutable(String file, Platform platform) throws IOException {
    final Path nodeModulesBin = getNodeModulesBin();
    if (nodeModulesBin != null) {
      final Path executable = nodeModulesBin.resolve(file);
      if (!executable.toFile().exists()) {
        throw new IOException(format("Could not find executable <%s>", executable));
      }
      return executable.toAbsolutePath().toString();
    } else {
      String root = getHome();
      if (root == null) {
        return null;
      }
      String wrapperFileName = "snyk".equals(file) ? platform.snykWrapperFileName : platform.snykToHtmlWrapperFileName;
      final Path executable = Paths.get(root).resolve(wrapperFileName);
      if (!executable.toFile().exists()) {
        throw new IOException(format("Could not find executable <%s>", wrapperFileName));
      }
      return executable.toAbsolutePath().toString();
    }
  }

  private Path getNodeModulesBin() {
    String root = getHome();
    if (root == null) {
      return null;
    }

    Path nodeModules = Paths.get(root).resolve("node_modules").resolve(".bin");
    if (!nodeModules.toFile().exists()) {
      return null;
    }

    return nodeModules;
  }

  @Extension
  @Symbol("snyk")
  public static class SnykInstallationDescriptor extends ToolDescriptor<SnykInstallation> {

    @Nonnull
    @Override
    public String getDisplayName() {
      return "Snyk";
    }

    @Override
    public List<? extends ToolInstaller> getDefaultInstallers() {
      return Collections.singletonList(new SnykInstaller(null, null, null));
    }

    @Override
    public SnykInstallation[] getInstallations() {
      Jenkins instance = Jenkins.getInstanceOrNull();
      if (instance == null) {
        throw new IllegalStateException("Jenkins has not been started, or was already shut down");
      }
      return instance.getDescriptorByType(SnykStepBuilderDescriptor.class).getInstallations();
    }

    @Override
    public void setInstallations(SnykInstallation... installations) {
      Jenkins instance = Jenkins.getInstanceOrNull();
      if (instance == null) {
        throw new IllegalStateException("Jenkins has not been started, or was already shut down");
      }
      instance.getDescriptorByType(SnykStepBuilderDescriptor.class).setInstallations(installations);
    }
  }
}
