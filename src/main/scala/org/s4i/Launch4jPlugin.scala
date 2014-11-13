package org.s4i

import java.io.File

import net.sf.launch4j.config._
import sbt._
import scala.collection.JavaConversions._

object Import {
  val launch4j = TaskKey[File]("build-launcher", "Create launch4j wrapper")
}

object Launch4jPlugin extends AutoPlugin {
  override def trigger = allRequirements

  val autoImport = Import
  import org.s4i.Launch4jPlugin.autoImport._
  import sbt.Keys._

  override val projectSettings = Seq(
    launch4j := runLaunch4j.value,
    mainClass in launch4j := None
  )

  private def runLaunch4j: Def.Initialize[Task[File]] = Def.task {
    val outputdir = target.value / "launch4j"
    val configXml = outputdir / "out.xml"
    val classpath = outputdir / "lib"
    val executable = outputdir / "launch4j" / "launch4jc.exe"
    val outfile = outputdir / "app.exe"

    outputdir.mkdirs()

    // copy the project artifact and all dependencies to /lib
    val jars = {
      val filter = configurationFilter(Compile.name)
      val files = (`package` in Compile).value +: update.value.matching(filter)
      IO.copy {
        files.map { file => file -> classpath / file.name }
      }
    }

    val conf = new Config()
    conf.setOutfile(outfile)
    conf.setDontWrapJar(true)
    conf.setClassPath {
      val paths = for {
        jar <- jars
        relative <- jar.relativeTo(outputdir)
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
      // TODO package JRE
      val jre = new Jre()
      jre.setMinVersion("1.8.0")
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
    val command = Process(executable.absolutePath, Seq(configXml.absolutePath))
    command !

    // TODO zip it up
    ???
  }
}
