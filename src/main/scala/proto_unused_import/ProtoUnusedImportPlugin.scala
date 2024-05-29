package proto_unused_import

import sbt.*
import sbt.Keys.*
import sbtprotoc.ProtocPlugin
import sbtprotoc.ProtocPlugin.autoImport.PB
import scala.collection.concurrent.TrieMap
import scala.sys.process.Process
import scala.sys.process.ProcessLogger

object ProtoUnusedImportPlugin extends AutoPlugin {
  private[this] val protobufUnusedWarningsLock = new Object

  object autoImport {
    val protoUnusedImportRemove = taskKey[Unit]("")
    val protoUnusedImportCheck = taskKey[Unit]("")
    val protoUnusedImportCheckAll = taskKey[Unit]("")
    val protoUnusedImportConvertPath = settingKey[Boolean]("")
  }

  import autoImport.*

  override def projectSettings: Seq[Setting[?]] = Def.settings(
    protoUnusedImportConvertPath := true,
    protoUnusedImportCheckAll := {
      (Compile / protoUnusedImportCheck).value
      (Test / protoUnusedImportCheck).value
    },
    protoUnusedImportSetting(Compile),
    protoUnusedImportSetting(Test),
  )

  private[this] val protobufUnusedWarnings: TrieMap[(String, String), List[UnusedWarn]] = TrieMap.empty

  override def requires: Plugins = ProtocPlugin

  private def separatorChar: Char = ':'
  private def unusedWarnLineSuffix: String = ".proto is unused."

  private case class UnusedWarn(file: String, line: Int, suffix: String) {
    override def toString: String = Seq(file, line, suffix).mkString(String.valueOf(separatorChar))
  }

  private object UnusedWarn {
    private object AsInt {
      def unapply(str: String): Option[Int] = {
        try {
          Option(Integer.parseInt(str))
        } catch {
          case _: NumberFormatException => None
        }
      }
    }
    def unapply(str: String): Option[UnusedWarn] = {
      // file-name:line-number:1: warning: Import google/protobuf/wrappers.proto is unused.
      PartialFunction.condOpt(str.split(separatorChar).toList) { case file :: AsInt(line) :: suffix =>
        UnusedWarn(file = file, line = line, suffix = suffix.mkString(String.valueOf(separatorChar)))
      }
    }
  }

  private case class Unused(file: File, line: Int, suffix: String)

  private def convertUnusedLines(dirs: Seq[File], warns: Seq[UnusedWarn]): Seq[Unused] = {
    warns.flatMap { warn =>
      dirs.map { dir =>
        val f = dir / warn.file
        if (f.isFile) {
          Option(Unused(dir / warn.file, warn.line, suffix = warn.suffix))
        } else {
          None
        }
      }.collectFirst { case Some(x) => x }
    }
  }

  def protoUnusedImportSetting(c: Configuration): Seq[Setting[?]] = Def.settings(
    c / protoUnusedImportCheck := {
      val _ = (c / PB.generate).value
      protobufUnusedWarningsLock.synchronized {
        val warns = protobufUnusedWarnings.get((state.value.currentProject.id, c.id)).toList.flatten
        assert(warns.isEmpty, warns.mkString(" "))
      }
    },
    c / protoUnusedImportRemove := {
      val _ = (c / PB.generate).value
      val s = state.value
      protobufUnusedWarningsLock.synchronized {
        val unusedLines = convertUnusedLines(
          dirs = (c / PB.protoSources).value,
          warns = protobufUnusedWarnings.get((state.value.currentProject.id, c.id)).toList.flatten
        )

        unusedLines.groupBy(_.file).foreach { case (file, unused) =>
          val lines = unused.map(_.line).toSet
          s.log.info(
            s"remove unused import. file = ${file.getAbsolutePath}, line = '${lines.toList.sorted.mkString(", ")}'"
          )
          val removed = IO
            .read(file)
            .linesIterator
            .zip(Iterator.from(1))
            .collect { case (line, lineNum) if !lines(lineNum) => line }
            .toList
          IO.writeLines(file, removed)
        }
        protobufUnusedWarnings.update((s.currentProject.id, c.id), Nil)
      }
    },
    c / PB.runProtoc := {
      val exec = PB.protocExecutable.value.getAbsolutePath
      val lock = new Object()
      val s = streams.value.log
      val result = collection.mutable.ListBuffer.empty[String]
      val logger = ProcessLogger(
        fout = (str: String) => {
          lock.synchronized {
            result += str
          }
          s.info(str)
        },
        ferr = (str: String) => {
          lock.synchronized {
            result += str
          }
          if (protoUnusedImportConvertPath.value) {
            convertUnusedLines(
              dirs = (c / PB.protoSources).value,
              warns = Option(str).filter(_.endsWith(unusedWarnLineSuffix)).flatMap(s => UnusedWarn.unapply(s)).toList
            ) match {
              case Nil =>
                s.warn(str)
              case values =>
                values.foreach { x =>
                  s.warn(s"${x.file.getCanonicalPath}:${x.line}:${x.suffix}")
                }
            }
          } else {
            s.warn(str)
          }
        }
      )
      protocbridge.ProtocRunner.fromFunction { case (args, extraEnv) =>
        try {
          Process(
            command = exec +: args,
            cwd = None,
            extraEnv *
          ).!(logger)
        } finally {
          protobufUnusedWarningsLock.synchronized {
            protobufUnusedWarnings.update(
              (state.value.currentProject.id, c.id),
              lock
                .synchronized(result.toList)
                .filter(_.endsWith(unusedWarnLineSuffix))
                .flatMap(s => UnusedWarn.unapply(s))
            )
          }
        }
      }
    }
  )
}
