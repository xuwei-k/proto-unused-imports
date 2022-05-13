import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")
enablePlugins(SbtPlugin, ScriptedPlugin)
name := "proto-unused-imports"
publishTo := sonatypePublishToBundle.value
Compile / unmanagedResources += (LocalRootProject / baseDirectory).value / "LICENSE.txt"
Compile / packageSrc / mappings ++= (Compile / managedSources).value.map { f =>
  (f, f.relativeTo((Compile / sourceManaged).value).get.getPath)
}
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
    Nil
  }
}
scalacOptions ++= {
  if (scalaBinaryVersion.value == "3") {
    Nil
  } else {
    Seq(
      "-Xsource:3",
    )
  }
}
scalacOptions ++= Seq(
  "-deprecation",
)
pomExtra :=
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
  releaseStepCommandAndRemaining("publishSigned"),
  releaseStepCommandAndRemaining("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
description := "sbt plugin for remove proto file unused imports"
scriptedLaunchOpts += "-Dplugin.version=" + version.value
scriptedBufferLog := false
