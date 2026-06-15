import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

def sbt2 = "2.0.0"

addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.1.0")

crossScalaVersions += scala_version_from_sbt_version.ScalaVersionFromSbtVersion(sbt2)

pluginCrossBuild / sbtVersion := {
  scalaBinaryVersion.value match {
    case "2.12" =>
      sbtVersion.value
    case _ =>
      sbt2
  }
}

libraryDependencies += {
  val v = (pluginCrossBuild / sbtBinaryVersion).value match {
    case "1.0" =>
      "1.0.8"
    case "2" =>
      "1.1.0-RC1"
  }
  Defaults.sbtPluginExtra(
    "com.thesamet" % "sbt-protoc" % v,
    (pluginCrossBuild / sbtBinaryVersion).value,
    (update / scalaBinaryVersion).value,
  )
}
enablePlugins(SbtPlugin, ScriptedPlugin)
name := "proto-unused-imports"
publishTo := (if (isSnapshot.value) None else localStaging.value)
Compile / unmanagedResources += (LocalRootProject / baseDirectory).value / "LICENSE.txt"
Compile / doc / scalacOptions ++= {
  val hash = sys.process.Process("git rev-parse HEAD").lineStream_!.head
  if (scalaBinaryVersion.value != "3") {
    Seq(
      "-sourcepath",
      (LocalRootProject / baseDirectory).value.getAbsolutePath,
      "-doc-source-url",
      s"https://github.com/xuwei-k/proto-unused-imports/blob/${hash}€{FILE_PATH}.scala"
    )
  } else {
    Seq(
      "-source-links:github://xuwei-k/proto-unused-imports",
      "-revision",
      hash
    )
  }
}
scalacOptions ++= {
  if (scalaBinaryVersion.value == "3") {
    Nil
  } else {
    Seq(
      "-release:8",
      "-Xsource:3",
    )
  }
}
scalacOptions ++= Seq(
  "-deprecation",
)
pomExtra := (
  <developers>
    <developer>
      <id>xuwei-k</id>
      <name>Kenji Yoshida</name>
      <url>https://github.com/xuwei-k</url>
    </developer>
  </developers>
  <scm>
    <url>git@github.com:xuwei-k/proto-unused-imports.git</url>
    <connection>scm:git:git@github.com:xuwei-k/proto-unused-imports.git</connection>
  </scm>
)
organization := "com.github.xuwei-k"
homepage := Some(url("https://github.com/xuwei-k/proto-unused-imports"))
licenses := List(
  "MIT License" -> url("https://opensource.org/licenses/mit-license")
)
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+ publishSigned"),
  releaseStepCommandAndRemaining("sonaRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
description := "sbt plugin for remove proto file unused imports"
scriptedLaunchOpts += "-Dplugin.version=" + version.value
scriptedBufferLog := false
