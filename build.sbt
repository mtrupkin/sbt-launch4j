import bintray.Keys._

sbtPlugin := true

name := "sbt-launch4j"

organization := "org.trupkin"

scalaVersion := "2.10.4"

licenses += ("BSD", url("http://www.opensource.org/licenses/bsd-license.html"))

publishMavenStyle := false

bintraySettings

releaseSettings

repository in bintray := "sbt-plugins"

bintrayOrganization in bintray := None

resolvers ++= Seq(
  "SpringSource" at "http://repository.springsource.com/maven/bundles/external",
  "Simulation @ TU Delft" at "http://simulation.tudelft.nl/maven/"
)

libraryDependencies += ("net.sf.launch4j" % "launch4j" % "3.5.0")
  .exclude("com.ibm.icu", "icu4j")
  .exclude("abeille", "net.java.abeille")

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-compress" % "1.9",
  "org.tukaani" % "xz" % "1.5",
  ("net.sf.launch4j" % "launch4j" % "3.5.0")
    .exclude("com.ibm.icu", "icu4j")
    .exclude("abeille", "net.java.abeille"))
