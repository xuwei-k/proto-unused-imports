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
  }

  import autoImport.*

  override def projectSettings: Seq[Setting[?]] = Def.settings(
    protoUnusedImportSetting(Compile),
    protoUnusedImportSetting(Test),
  )

  private[this] val protobufUnusedWarnings: TrieMap[(String, String), List[String]] = TrieMap.empty

  override def requires: Plugins = ProtocPlugin

  def protoUnusedImportSetting(c: Configuration): Seq[Setting[?]] = Def.settings(
    c / protoUnusedImportRemove := {
      val _ = (c / PB.generate).value
      val log = streams.value.log
      val s = state.value
      case class Unused(file: File, line: Int)
      protobufUnusedWarningsLock.synchronized {
        (c / PB.protoSources).value.headOption.foreach { dir =>
          val warns = protobufUnusedWarnings.get((state.value.currentProject.id, c.id)).toList.flatten
          val unusedLines = warns.flatMap { warn =>
            // file-name:line-number:1: warning: Import google/protobuf/wrappers.proto is unused.
            warn.split(':').toList match {
              case file :: unused :: _ =>
                val f = dir / file
                Unused(f, unused.toInt) :: Nil
              case _ =>
                log.error(s"unexpected unused warning ${warn}")
                Nil
            }
          }

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
      }
    },
    c / PB.runProtoc := {
      val exec = PB.protocExecutable.value.getAbsolutePath
      val lock = new Object
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
          s.warn(str)
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
              lock.synchronized(result.toList).filter(_.endsWith(".proto is unused."))
            )
          }
        }
      }
    }
  )
}
