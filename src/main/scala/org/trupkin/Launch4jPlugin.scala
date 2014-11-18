package org.trupkin

import java.io.{File, SequenceInputStream, BufferedInputStream, FileInputStream}
import java.nio.file.Files
import org.apache.commons.compress.archivers.sevenz._

import net.sf.launch4j.config._
import sbt._
import scala.collection.JavaConversions._

object Import {
  val buildSfx = TaskKey[File]("build-sfx", "Create self extracting")
  val buildLauncher = TaskKey[File]("build-launcher", "Create launch4j wrapper")

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
    mainClass in buildLauncher := None,
    sfxTargetFilename := s"${name.value}-${version.value}.exe",
    launcherExecutableFilename := s"${name.value}.exe"
  )

  private def runLaunch4j: Def.Initialize[Task[File]] = Def.task {
    val outputdir = target.value / "launcher"
    val distdir = outputdir / "build"
    val configXml = outputdir / "launch4j.xml"
    val classpath = distdir / "lib"
    val launch4jExecutable = outputdir / "launch4j" / "launch4jc.exe"
    val outfile = distdir / launcherExecutableFilename .value

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

    distdir
  }

  private def runCompress: Def.Initialize[Task[File]] = Def.task {
    val compressSourceDirectory = buildLauncher.value
    val compressBaseDirectory = target.value / "compress"
    val compressTargetDirectoryName = s"${name.value}-${version.value}"
    val compressTargetFile = compressBaseDirectory / s"${name.value}.7z"
    val sfxTargetFile = compressBaseDirectory / sfxTargetFilename.value

    // clean directory
    IO.delete(compressBaseDirectory)
    compressBaseDirectory.mkdirs()

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
