import bintray.Keys._

sbtPlugin := true

name := "sbt-launch4j"

organization := "org.trupkin"

version := "0.0.2"

scalaVersion := "2.10.4"

publishMavenStyle := false

bintraySettings

repository in bintray := "sbt-plugins"

licenses += ("BSD", url("http://www.opensource.org/licenses/bsd-license.html"))

bintrayOrganization in bintray := None

resolvers ++= Seq(
  "SpringSource" at "http://repository.springsource.com/maven/bundles/external",
  "Simulation @ TU Delft" at "http://simulation.tudelft.nl/maven/"
)

libraryDependencies += ("net.sf.launch4j" % "launch4j" % "3.5.0")
  .exclude("com.ibm.icu", "icu4j")
  .exclude("abeille", "net.java.abeille")
