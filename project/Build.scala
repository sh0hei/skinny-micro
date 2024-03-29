import sbt._, Keys._
import skinny.scalate.ScalatePlugin._, ScalateKeys._
import skinny.servlet._, ServletPlugin._, ServletKeys._

import scala.language.postfixOps

object SkinnyMicroBuild extends Build {

  lazy val currentVersion = "2.0.0-SNAPSHOT"
  lazy val skinnyVersion = "2.0.0.M4"
  // Scalatra 2.4 will be incompatible with Skinny
  lazy val compatibleScalatraVersion = "2.3.1"
  // TODO: fix version before skinny 2.0.0
  lazy val json4SVersion = "3.3.0.RC3"
  lazy val scalikeJDBCVersion = "2.2.8"
  lazy val h2Version = "1.4.188"
  lazy val kuromojiVersion = "5.2.1"
  lazy val mockitoVersion = "1.10.19"
  // Jetty 9.3 dropped Java 7
  lazy val jettyVersion = "9.2.13.v20150730"
  lazy val logbackVersion = "1.1.3"
  lazy val slf4jApiVersion = "1.7.12"
  lazy val scalaTestVersion = "2.2.5"

  lazy val baseSettings = Seq(
    organization := "org.skinny-framework",
    version := currentVersion,
    dependencyOverrides += "org.slf4j" %  "slf4j-api"  % slf4jApiVersion,
    resolvers ++= Seq(
      "sonatype releases"  at "https://oss.sonatype.org/content/repositories/releases"
      ,"sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    ),
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    publishMavenStyle := true,
    sbtPlugin := false,
    scalaVersion := "2.11.7",
    ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature"),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { x => false },
    transitiveClassifiers in Global := Seq(Artifact.SourceClassifier),
    incOptions := incOptions.value.withNameHashing(true),
    logBuffered in Test := false,
    javaOptions in Test ++= Seq("-Dskinny.env=test"),
    updateOptions := updateOptions.value.withCachedResolution(true),
    javacOptions ++= Seq("-source", "1.7", "-target", "1.7", "-encoding", "UTF-8", "-Xlint:-options"),
    javacOptions in doc := Seq("-source", "1.7"),
    pomExtra := {
      <url>http://skinny-framework.org/</url>
        <licenses>
          <license>
            <name>MIT License</name>
            <url>http://www.opensource.org/licenses/mit-license.php</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:skinny-framework/skinny-micro.git</url>
          <connection>scm:git:git@github.com:skinny-framework/skinny-micro.git</connection>
        </scm>
        <developers>
          <developer>
            <id>seratch</id>
            <name>Kazuhiro Sera</name>
            <url>http://git.io/sera</url>
          </developer>
        </developers>
    }
  )

  // -----------------------------
  // skinny libraries

  lazy val micro = Project(id = "micro", base = file("micro"),
    settings = baseSettings ++ Seq(
      name := "skinny-micro",
      libraryDependencies <++= (scalaVersion) { (sv) =>
        Seq(
          "org.skinny-framework" %% "skinny-common"   % skinnyVersion             % Compile,
          "org.scalatra"      %% "scalatra-specs2"    % compatibleScalatraVersion % Test,
          "org.scalatra"      %% "scalatra-scalatest" % compatibleScalatraVersion % Test,
          "com.typesafe.akka" %% "akka-actor"         % "2.3.12"                  % Test
        ) ++ testDependencies ++ servletApiDependencies ++ slf4jApiDependencies ++ Seq(
          "org.scalatra.rl"                  %% "rl"                % "0.4.10"  % Compile,
          "com.googlecode.juniversalchardet" %  "juniversalchardet" % "1.0.3"   % Compile
        )
      }
    )
  )

  lazy val microJson = Project(id = "microJson", base = file("micro-json"),
    settings = baseSettings ++ Seq(
      name := "skinny-micro-json",
      libraryDependencies ++= servletApiDependencies ++ json4sDependencies ++ Seq(
        "org.scalatra"      %% "scalatra-specs2"    % compatibleScalatraVersion % Test,
        "org.scalatra"      %% "scalatra-scalatest" % compatibleScalatraVersion % Test,
        "com.typesafe.akka" %% "akka-actor"         % "2.3.12"                  % Test
      ) ++ testDependencies
    )
  ).dependsOn(
    micro
  )

  lazy val microScalate = Project(id = "microScalate", base = file("micro-scalate"),
    settings = baseSettings ++ Seq(
      name := "skinny-micro-scalate",
      libraryDependencies ++= servletApiDependencies ++ Seq(
        "org.scalatra.scalate"  %% "scalate-core"       % "1.7.1" excludeAll(fullExclusionRules: _*),
        "org.scalatra"          %% "scalatra-specs2"    % compatibleScalatraVersion % Test,
        "org.scalatra"          %% "scalatra-scalatest" % compatibleScalatraVersion % Test,
        "com.typesafe.akka"     %% "akka-actor"         % "2.3.12"                  % Test
      ) ++ testDependencies
    )
  ).dependsOn(
    micro
  )

  lazy val microServer = Project(id = "microServer", base = file("micro-server"),
    settings = baseSettings ++ Seq(
      name := "skinny-micro-server",
      libraryDependencies ++= jettyDependencies ++ testDependencies ++ Seq(
        "org.skinny-framework" %% "skinny-http-client" % skinnyVersion % Test
      )
    )
  ).dependsOn(
    micro,
    microJson % Test
  )

  lazy val microTest = Project(id = "microTest", base = file("micro-test"),
    settings = baseSettings ++ Seq(
      name := "skinny-micro-test",
      libraryDependencies ++= servletApiDependencies ++ Seq(
        "junit"              %  "junit"            % "4.12"       % Compile,
        "org.apache.commons" %  "commons-lang3"    % "3.4"        % Compile,
        "org.eclipse.jetty"  %  "jetty-webapp"     % jettyVersion % Compile,
        "org.apache.httpcomponents" % "httpclient" % "4.5"        % Compile,
        "org.apache.httpcomponents" % "httpmime"   % "4.5"        % Compile,
        "org.scalatest"      %% "scalatest"        % scalaTestVersion % Compile
      )
    )
  ).dependsOn(
    micro
  )

  // -----------------------------
  // common dependencies

  lazy val fullExclusionRules = Seq(
    ExclusionRule("org.slf4j", "slf4j-api"),
    ExclusionRule("joda-time", "joda-time"),
    ExclusionRule("org.joda",  "joda-convert"),
    ExclusionRule("log4j",     "log4j"),
    ExclusionRule("org.slf4j", "slf4j-log4j12")
  )
  lazy val servletApiDependencies = Seq(
    "javax.servlet" % "javax.servlet-api" % "3.0.1"  % Provided
  )
  lazy val slf4jApiDependencies   = Seq(
    "org.slf4j"     % "slf4j-api"         % slf4jApiVersion % Compile
  )
  lazy val json4sDependencies = Seq(
    "org.json4s"    %% "json4s-jackson"     % json4SVersion    % Compile  excludeAll(fullExclusionRules: _*),
    "org.json4s"    %% "json4s-native"      % json4SVersion    % Provided excludeAll(fullExclusionRules: _*),
    "org.json4s"    %% "json4s-ext"         % json4SVersion    % Compile  excludeAll(fullExclusionRules: _*)
  )
  lazy val jettyDependencies = Seq(
    "javax.servlet"     %  "javax.servlet-api" % "3.0.1"       % Compile,
    "org.eclipse.jetty" %  "jetty-webapp"      % jettyVersion  % Compile,
    "org.eclipse.jetty" %  "jetty-servlet"     % jettyVersion  % Compile,
    "org.eclipse.jetty" %  "jetty-server"      % jettyVersion  % Compile
  )
  lazy val testDependencies = Seq(
    "org.scalatest"           %% "scalatest"       % scalaTestVersion % Test,
    "org.mockito"             %  "mockito-core"    % mockitoVersion   % Test,
    "ch.qos.logback"          %  "logback-classic" % logbackVersion   % Test
  )

}
