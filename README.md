# SBT-LAUNCH4j
Create an executable for your jvm application without requiring a JRE to be previously installed.

Uses [launch4j](http://launch4j.sourceforge.net) to create an executable wrapper for your jvm based project.

## Project Setup
Create a new file in your project at `./project/launcher.sbt` and add the following lines.

    resolvers += Resolver.url("org.trupkin sbt plugins", url("http://dl.bintray.com/mtrupkin/sbt-plugins/"))(Resolver.ivyStylePatterns)

    addSbtPlugin("org.trupkin" % "sbt-launch4j" % "0.1.0")  

## Usage
A new `build-launcher` task is now available that creates a zip file that contains the generated executable wrapper, the application jar and dependencies.

    > build-launcher
