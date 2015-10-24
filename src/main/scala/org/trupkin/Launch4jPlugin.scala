package org.trupkin

import java.io.File
import java.io._
import java.nio.file.Files
import net.sf.launch4j.{Log, Builder}
import org.apache.commons.compress.archivers.sevenz._

import net.sf.launch4j.config._
import sbt._
import scala.collection.JavaConversions._

object Import {
  val buildSfx = TaskKey[File]("build-sfx", "Create self extracting")
  val buildLauncher = TaskKey[File]("build-launcher", "Create launch4j wrapper")

  val jreHome = SettingKey[File]("jre-home", "Location of jre runtime")
  val launcherExecutableFilename = SettingKey[String]("launcher-executable-filename", "Launcher executable filename")
  val sfxTargetFilename = SettingKey[String]("sxf-target-filename", "Self extracting filename")
}

object Launch4jPlugin extends AutoPlugin {
  override def trigger = allRequirements

  val autoImport = Import
  import org.trupkin.Launch4jPlugin.autoImport._
  import sbt.Keys._

  override val projectSettings = Seq(
    buildLauncher := runLaunch4j.value,
    buildSfx := runCompress.value,
    (target in buildLauncher) :=  target.value / "sbt-launch4j",
    mainClass in buildLauncher := None,
    sfxTargetFilename := s"${name.value}-${version.value}.exe",
    launcherExecutableFilename := s"${name.value}.exe",
    jreHome := new java.io.File(Option(System.getenv("JAVA_HOME")).getOrElse {
      throw new IllegalStateException("Missing JAVA_HOME")
    }) / "jre"
  )

  private def runLaunch4j: Def.Initialize[Task[File]] = Def.task {
    val outputdir = (target in buildLauncher).value
    val launch4jUtilDir = outputdir / "launch4j-util"
    val distdir = outputdir / "app"
    val configXml = launch4jUtilDir / "launch4j.xml"
    val classpath = distdir / "lib"
    val launch4jExecutable = launch4jUtilDir / "launch4j" / "launch4jc.exe"
    val outfile = distdir / launcherExecutableFilename .value

    // clean directory
    IO.delete(distdir)
    distdir.mkdirs()
    launch4jUtilDir.mkdirs()

    // copy the project artifact and all dependencies to /lib
    val jars = {
      val filter = configurationFilter(Compile.name)
      val files = (`package` in Compile).value +: update.value.matching(filter)
      IO.copy {
        files.map { file => file -> classpath / file.name }
      }
    }

    // package jre
    val jre = jreHome.value
    val jreFiles = for {
      file <- jre.***.get //if file.isFile
      name <- file.relativeTo(jre)
    } yield file -> name.toString

    IO.copy {
      jreFiles.map { case (file: File, path: String) => (file, distdir / "jre" / path) }
    }

    ConfigPersister.getInstance.createBlank()
    val conf: Config = ConfigPersister.getInstance.getConfig
    conf.setOutfile(outfile)
    conf.setDontWrapJar(true)
    conf.setHeaderType("console")
    conf.setStayAlive(true)
    conf.setClassPath {
      val paths = for {
        jar <- jars
        relative <- jar.relativeTo(distdir)
      } yield relative.getPath

      val cp = new ClassPath()
      cp.setMainClass {
        (mainClass in buildLauncher).value getOrElse {
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
      jre.setBundledJre64Bit(true)
      jre
    }
    conf.validate()

    // generate the launch4j build config file
    val persister = ConfigPersister.getInstance()
    persister.setAntConfig(conf, baseDirectory.value)
    persister.save(configXml)


    // The launch4j jar dependency does not contain
    // all the resources required to run it.
    // The jar assumes that a launch4j installation exists.
    // Otherwise we could do this:
    // new Builder(Log.getConsoleLog).build()

    // extract launch4j executable
    val url = getClass.getClassLoader.getResource("launch4j-3.8-win32.zip")
    IO.unzipURL(url, launch4jUtilDir)

    // run launch4j
    val command = Process(launch4jExecutable.absolutePath, Seq(configXml.absolutePath))
    command !

    distdir
  }

  private def runCompress: Def.Initialize[Task[File]] = Def.task {
    val compressSourceDirectory = buildLauncher.value
    val compressTargetDirectory = (target in buildLauncher).value / "zip"
    val compressTargetDirectoryName = s"${name.value}-${version.value}"
    val compressTargetFile = compressTargetDirectory / s"${name.value}.7z"
    val sfxTargetFile = (target in buildLauncher).value / sfxTargetFilename.value

    // clean directory
    IO.delete(compressTargetDirectory)
    compressTargetDirectory.mkdirs()

    val sfxURL =  getClass.getClassLoader.getResource("sfx/windows/7z.sfx")

    // compress distribution
    val compressFiles = for {
      file <- compressSourceDirectory.**(new SimpleFileFilter(f=>f.isFile)).get
      filename <- file.relativeTo(compressSourceDirectory)
    } yield file -> (new File(compressTargetDirectoryName) / filename.toString).toString

    val sevenZOutput = new SevenZOutputFile(compressTargetFile)
    sevenZOutput.setContentCompression(SevenZMethod.LZMA2)
    for {
      e <- compressFiles
    } yield {
      val entry = sevenZOutput.createArchiveEntry(e._1, e._2)
      sevenZOutput.putArchiveEntry(entry)
      writeEntry(sevenZOutput, e._1)
      sevenZOutput.closeArchiveEntry()
    }
    sevenZOutput.close()

    val sequence = new SequenceInputStream(new BufferedInputStream(sfxURL.openStream()), new BufferedInputStream(new FileInputStream(compressTargetFile)))
    Files.copy(sequence, sfxTargetFile.toPath)

    sfxTargetFile
  }

  def writeEntry(sz: SevenZOutputFile, f: File): Unit = {
    val is = new BufferedInputStream(new FileInputStream(f))

    var byte = 0
    while (byte >= 0) {
      byte = is.read
      if (byte >= 0) sz.write(byte)
    }

    is.close()
  }
}
