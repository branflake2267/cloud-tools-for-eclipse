package com.google.cloud.tools.eclipse.appengine.facets;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.junit.Assert;
import org.junit.Test;

public class FacetUninstallDelegateTest {
  @Test
  public void testUpdateMavenProjectDependecies_nonAppEngineInitialDependency() {
    Dependency nonAppEngineDependency = new Dependency();
    nonAppEngineDependency.setGroupId("groupId");
    nonAppEngineDependency.setArtifactId("artifactId");
    nonAppEngineDependency.setVersion("version");
    nonAppEngineDependency.setScope("scope");

    List<Dependency> intialDependencies = new ArrayList<Dependency>();
    intialDependencies.add(nonAppEngineDependency);

    List<Dependency> finalDependencies = FacetUninstallDelegate.updateMavenProjectDependecies(intialDependencies);
    Assert.assertEquals(1, finalDependencies.size());
  }

  @Test
  public void testUpdateMavenProjectDependecies_appEngineInitialDependency() {
    Dependency appEngineApiStubsDependency = new Dependency();
    appEngineApiStubsDependency.setGroupId("com.google.appengine");
    appEngineApiStubsDependency.setArtifactId("appengine-api-stubs");
    appEngineApiStubsDependency.setVersion("${appengine.version}");
    appEngineApiStubsDependency.setScope("test");

    List<Dependency> intialDependencies = new ArrayList<Dependency>();
    intialDependencies.add(appEngineApiStubsDependency);

    List<Dependency> finalDependencies = FacetUninstallDelegate.updateMavenProjectDependecies(intialDependencies);
    Assert.assertTrue(finalDependencies.isEmpty());
  }

  @Test
  public void testUpdateMavenProjectDependecies_noInitialDependency() {
    List<Dependency> initialDependencies = new ArrayList<Dependency>();
    List<Dependency> finalDependencies1 = FacetUninstallDelegate.updateMavenProjectDependecies(initialDependencies);
    List<Dependency> finalDependencies2 = FacetUninstallDelegate.updateMavenProjectDependecies(null);

    Assert.assertTrue(finalDependencies1.isEmpty());
    Assert.assertTrue(finalDependencies2.isEmpty());
  }
  
  @Test
  public void testUpdatePomProperties_nonAppEngineInitialPropertery() {
    Properties properties = new Properties();
    properties.setProperty("a", "b");

    FacetUninstallDelegate.updatePomProperties(properties);
    Assert.assertEquals(1, properties.size());
    Assert.assertTrue(properties.containsKey("a"));
  }
  
  @Test
  public void testUpdatePomProperties_appEngineInitialPropertery() {
    Properties properties = new Properties();
    properties.setProperty("app.version", "1");

    FacetUninstallDelegate.updatePomProperties(properties);
    Assert.assertTrue(properties.isEmpty());
  }
  
  @Test
  public void testUpdatePomProperties_noInitialPropertery() {
    Properties properties = new Properties();

    FacetUninstallDelegate.updatePomProperties(properties);
    Assert.assertTrue(properties.isEmpty());
  }
}