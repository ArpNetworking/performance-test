Performance Test
================

<a href="https://raw.githubusercontent.com/ArpNetworking/performance-test/master/LICENSE">
    <img src="https://img.shields.io/hexpm/l/plug.svg"
         alt="License: Apache 2">
</a>
<a href="https://travis-ci.org/ArpNetworking/performance-test/">
    <img src="https://travis-ci.org/ArpNetworking/performance-test.png"
         alt="Travis Build">
</a>
<a href="http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.arpnetworking.test%22%20a%3A%22performance-test%22">
    <img src="https://img.shields.io/maven-central/v/com.arpnetworking.test/performance-test.svg"
         alt="Maven Artifact">
</a>

Extensions to [JUnit](http://junit.org/) and [JUnitBenchmarks](https://labs.carrotsearch.com/junit-benchmarks.html) to execute performance tests with results published in JSON and support for profiling cpu of each test execution with hprof.

Setup
-----

### Add Dependency

Determine the latest version of the performance test in [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.arpnetworking.metrics%22%20a%3A%22jvm-extra%22).

#### Maven

Add a dependency to your pom:

```xml
<dependency>
    <groupId>com.arpnetworking.metrics.extras</groupId>
    <artifactId>jvm-extra</artifactId>
    <version>VERSION</version>
    <scope>test</scope>
</dependency>
```

The Maven Central repository is included by default.

#### Gradle

Add a dependency to your build.gradle:

    testCompile group: 'com.arpnetworking.metrics.extras', name: 'jvm-extra', version: 'VERSION'

Add the Maven Central Repository into your *build.gradle*:

```groovy
repositories {
    mavenCentral()
}
```

#### SBT

Add a dependency to your project/Build.scala:

```scala
val appDependencies = Seq(
    "com.arpnetworking.test" % "performance-test" % "VERSION" % Test
)
```

The Maven Central repository is included by default.

### Test Definition

First, create a test class with a new relevant suffix (e.g. "PT.java" or "PerformanceTest.java") and annotate the test class with BenchmarkOptions:

```java
@BenchmarkOptions(callgc = true, benchmarkRounds = 10, warmupRounds = 5)
```

Then, define a __static__ JsonBenchmarkConsumer and __non-static__ TestRule. The specified path in the consumer declaration is for the JSON test results file. For example:

```java
private static final JsonBenchmarkConsumer JSON_BENCHMARK_CONSUMER = new JsonBenchmarkConsumer(
        Paths.get("target/site/perf/sample-performance-test.json"));
@Rule
public final TestRule _benchMarkRule = new BenchmarkRule(JSON_BENCHMARK_CONSUMER);
```

Next, in your pom.xml file define a profile for executing your performance tests:

```xml
<profiles>
  <profile>
    <id>performanceTest</id>
    <activation>
      <activeByDefault>false</activeByDefault>
    </activation>
    <build>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <executions>
            <execution>
              <id>default-test</id>
              <phase>test</phase>
              <goals>
                <goal>test</goal>
              </goals>
              <configuration>
                <includes>
                  <include>**/*PerformanceTest.java</include>
                </includes>
                <parallel combine.self="override" />
              </configuration>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </build>
  </profile>
</profiles>
```
The key points in the profile are:

* Overrides the default test configuration.
* Executes only tests ending with _PerformanceTest.java_.
* Ensures tests are executed serially.

Finally, define your test methods and execute:

    > mvn -PperformanceTest test

The results are written out as JSON to the file specified in the consumer. Note that each test method has its own object inside the array.

```json
[{"result":{"benchmarkRounds":10,"warmupRounds":5,"warmupTime":842,"benchmarkTime":1690,"roundAverage":{"avg":0.15380000000000002,"stddev":0.005211525688317791},"blockedAverage":{"avg":0.0,"stddev":0.0},"gcAverage":{"avg":0.0175,"stddev":0.001284523257866504},"gcInfo":{"accumulatedInvocations":96,"accumulatedTime":422},"threadCount":1,"shortTestClassName":"SampleParameterizedPerformanceTest","testClass":"com.arpnetworking.test.junitbenchmarks.SampleParameterizedPerformanceTest","testMethod":null,"testClassName":"com.arpnetworking.test.junitbenchmarks.SampleParameterizedPerformanceTest","testMethodName":"test[constructor]"},"profileFile":null},{"result":{"benchmarkRounds":10,"warmupRounds":5,"warmupTime":2127,"benchmarkTime":4093,"roundAverage":{"avg":0.3942,"stddev":0.016987053894069647},"blockedAverage":{"avg":0.0,"stddev":0.0},"gcAverage":{"avg":0.0169,"stddev":7.000000000000472E-4},"gcInfo":{"accumulatedInvocations":36,"accumulatedTime":149},"threadCount":1,"shortTestClassName":"SampleParameterizedPerformanceTest","testClass":"com.arpnetworking.test.junitbenchmarks.SampleParameterizedPerformanceTest","testMethod":null,"testClassName":"com.arpnetworking.test.junitbenchmarks.SampleParameterizedPerformanceTest","testMethodName":"test[new_instance]"},"profileFile":null}]
```

Additionally, there are two sample performance tests in the test directory for reference:

* [SamplePerformanceTest.java](test/java/com/arpnetworking/test/junitbenchmarks/SamplePerformanceTest.java)
* [SampleParameterizedPerformanceTest.java](test/java/com/arpnetworking/test/junitbenchmarks/SampleParameterizedPerformanceTest.java)

### Profile Generation

To enable profiling with performance tests first add the following to the surefire plugin configuration in your performance test profile:

```xml
<forkMode>always</forkMode>
<forkCount>1</forkCount>
<reuseForks>false</reuseForks>
<argLine combine.self="override">-agentlib:hprof=cpu=samples,depth=15,interval=10,force=y,verbose=y,doe=n,file=${basedir}/target/perf.profile.hprof.txt</argLine>
```

The key points in the configuration are:

* Fork for each test class execution.
* Do __not__ reuse forks.
* Enable the hprof java agent.
* Enable cpu profiling.
* Specify the output file.
* Overwrite the output file if necessary.
* Do not dump profiling information on exit.

Next, we also recommend resetting the cpu profile before each test suite execution by adding the following to each performance test:

```java
@BeforeClass
public static void setUp() {
    JSON_BENCHMARK_CONSUMER.prepareClass();
}
```

This prevents test framework setup cost/time from counting towards the profile of the first test.

Building
--------

Prerequisites:
* [JDK8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)

Building:

    performance-test> ./mvnw verify

To use the local version you must first install it locally:

    performance-test> ./mvnw install

You can determine the version of the local build from the pom file.  Using the local version is intended only for testing or development.

You may also need to add the local repository to your build in order to pick-up the local version:

* Maven - Included by default.
* Gradle - Add *mavenLocal()* to *build.gradle* in the *repositories* block.
* SBT - Add *resolvers += Resolver.mavenLocal* into *project/plugins.sbt*.

License
-------

Published under Apache Software License 2.0, see LICENSE

&copy; Groupon Inc., 2014
