enablePlugins(ProtoUnusedImportPlugin)

libraryDependencies += "com.google.protobuf" % "protobuf-java" % PB.protocVersion.value % "protobuf"

Compile / PB.targets := Seq(
  PB.gens.java(PB.protocVersion.value) -> (Compile / sourceManaged).value
)

Test / PB.targets := Seq(
  PB.gens.java(PB.protocVersion.value) -> (Test / sourceManaged).value
)
