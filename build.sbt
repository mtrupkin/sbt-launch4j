sbtPlugin := true

name := "sbt-launch4j"

organization := "org.s4i"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.10.4"

resolvers ++= Seq(
  "SpringSource" at "http://repository.springsource.com/maven/bundles/external",
  "Simulation @ TU Delft" at "http://simulation.tudelft.nl/maven/"
)

//resolvers ++= Seq(
//  "Typesafe Releases Repository" at "http://repo.typesafe.com/typesafe/releases/",
//  Resolver.url("sbt snapshot plugins", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots"))(Resolver.ivyStylePatterns),
//  Resolver.sonatypeRepo("snapshots"),
//  "Typesafe Snapshots Repository" at "http://repo.typesafe.com/typesafe/snapshots/",
//  Resolver.mavenLocal
//)

libraryDependencies += ("net.sf.launch4j" % "launch4j" % "3.5.0")
  .exclude("com.ibm.icu", "icu4j")
  .exclude("abeille", "net.java.abeille")