package org.trupkin

import java.io.File

import net.sf.launch4j.config._
import sbt._
import scala.collection.JavaConversions._

object Import {
  val launch4j = TaskKey[File]("build-launcher", "Create launch4j wrapper")
  val executable = SettingKey[String]("executable", "Launcher executable filename")
  val zipFilename = SettingKey[String]("zipFilename", "Zipped filename")
}

object Launch4jPlugin extends AutoPlugin {
  override def trigger = allRequirements

  val autoImport = Import
  import org.trupkin.Launch4jPlugin.autoImport._
  import sbt.Keys._

  override val projectSettings = Seq(
    launch4j := runLaunch4j.value,
    mainClass in launch4j := None,
    zipFilename := s"${name.value}-${version.value}.zip",
    executable := s"${name.value}.exe"
  )

  private def runLaunch4j: Def.Initialize[Task[File]] = Def.task {
    val outputdir = target.value / "launcher"
    val distdir = outputdir / "build"
    val configXml = outputdir / "launch4j.xml"
    val classpath = distdir / "lib"
    val launch4jExecutable = outputdir / "launch4j" / "launch4jc.exe"
    val outfile = distdir / executable .value

    // clean directory
    IO.delete(outputdir)
    distdir.mkdirs()

    // copy the project artifact and all dependencies to /lib
    val jars = {
      val filter = configurationFilter(Compile.name)
      val files = (`package` in Compile).value +: update.value.matching(filter)
      IO.copy {
        files.map { file => file -> classpath / file.name }
      }
    }

    // package jre
    val java = new File(Option(System.getenv("JAVA_HOME")).getOrElse {
      throw new IllegalStateException("Missing JAVA_HOME")
    })
    val jre = java / "jre"
    val jreFiles = for {
      file <- jre.***.get //if file.isFile
      name <- file.relativeTo(java)
    } yield file -> name.toString

    IO.copy {
      jreFiles.map { case (file: File, path: String) => (file, distdir / path) }
    }

    val conf = new Config()
    conf.setOutfile(outfile)
    conf.setDontWrapJar(true)
    conf.setClassPath {
      val paths = for {
        jar <- jars
        relative <- jar.relativeTo(distdir)
      } yield relative.getPath

      val cp = new ClassPath()
      cp.setMainClass {
        (mainClass in launch4j).value getOrElse {
          (discoveredMainClasses in Compile).value match {
            case Seq() => sys.error("No main classes were found")
            case Seq(single) => single
            case multiple => sys.error("Multiple main classes found: " + multiple)
          }
        }
      }
      cp.setPaths(paths.toList)
      cp
    }

    conf.setJre {
      val jre = new Jre()
      jre.setPath("jre")
      jre
    }
    conf.validate()

    // generate the launch4j build config file
    val persister = ConfigPersister.getInstance()
    persister.setAntConfig(conf, baseDirectory.value)
    persister.save(configXml)

    // extract launch4j executable
    val url = getClass.getClassLoader.getResource("launch4j-3.5-win32.zip")
    IO.unzipURL(url, outputdir)

    // run launch4j
    val command = Process(launch4jExecutable.absolutePath, Seq(configXml.absolutePath))
    command !

    val zipFile = target.value / zipFilename.value
    // zip up distribution
    val zipFiles = for {
      file <- distdir.***.get
      name <- file.relativeTo(distdir)
    } yield file -> name.toString

    IO.zip(zipFiles, zipFile)
    zipFile
  }
}
