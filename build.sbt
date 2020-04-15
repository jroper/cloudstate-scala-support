organization := "io.cloudstate"
name := "cloudstate-scala-support"
version := "0.0.1-SNAPSHOT"

val AkkaVersion = "2.6.1"
val AkkaHttpVersion = "10.1.11"
val ProtobufVersion = "3.9.0"

scalaVersion := "2.12.9"

offline := true

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-core" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http2-support" % AkkaHttpVersion,
  "com.google.protobuf" % "protobuf-java" % ProtobufVersion % "protobuf",
  "com.google.protobuf" % "protobuf-java-util" % ProtobufVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "org.slf4j" % "slf4j-simple" % "1.7.26",

)

enablePlugins(AkkaGrpcPlugin)

// Akka gRPC adds all protobuf files from the classpath to this, which we don't want because it includes
// all the Google protobuf files which are already compiled and on the classpath by ScalaPB. So we set it
// back to just our source directory.
PB.protoSources in Compile := Seq(
  (sourceDirectory in Compile).value / "proto"
)
PB.protoSources in Test := Seq()
// Akka gRPC overrides the default ScalaPB setting including the file base name, let's override it right back.
akkaGrpcCodeGeneratorSettings := Seq()
//akkaGrpcGeneratedSources in Compile := Seq(AkkaGrpc.Server)
//akkaGrpcGeneratedLanguages in Compile := Seq(AkkaGrpc.Scala)