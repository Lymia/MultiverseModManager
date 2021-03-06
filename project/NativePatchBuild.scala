/*
 * Copyright (c) 2015-2017 Lymia Alusyia <lymia@lymiahugs.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import java.util.UUID

import sbt._
import sbt.Keys._
import Config._
import Utils._

object NativePatchBuild {
  def win32_cc(p: Seq[Any]) = runProcess(config_win32_cc +: s"--target=$config_target_win32" +: p)
  def macos_cc(p: Seq[Any]) = runProcess(config_macos_cc +: s"--target=$config_target_macos" +: p)
  def linux_cc(p: Seq[Any]) = runProcess(config_linux_cc +: s"--target=$config_target_linux" +: p)
  def nasm    (p: Seq[Any]) = runProcess(config_nasm +: p)

  // Steam runtime
  def extractSteamRuntime[T](source: File, target: File, beforeLog: => T = null)(fn: (File, File) => Unit) =
    if(target.exists) target else {
      beforeLog
      IO.withTemporaryDirectory { temp =>
        runProcess(Seq("ar", "xv", source), temp)
        runProcess(Seq("tar", "xvf", temp / "data.tar.gz"), temp)
        fn(temp, target)
      }
      target
    }

  // Codegen for the macOS proxy (to deal with unexported symbols in Civ V's binary).
  def generateProxyDefine(file: File, target: File) {
    val lines = IO.readLines(file).filter(_.nonEmpty)
    val proxies = for((symbol, i) <- IO.readLines(file).filter(_.nonEmpty).zipWithIndex
                      if !symbol.startsWith("#") && !symbol.trim.isEmpty) yield {
      (s"""static const char* ${symbol}_name = "$symbol";
          |__attribute__((section("MPPATCH_PROXY,MPPATCH_PROXY"), aligned(8))) char $symbol[8] asm ("_$symbol");
        """.stripMargin,
       s"setupProxyFunction($symbol, ${symbol}_name);")
    }

    IO.write(target,
      s"""#import "c_rt.h"
         |#import "platform.h"
         |
         |${proxies.map(_._1.trim).mkString("\n")}
         |__attribute__((constructor(CONSTRUCTOR_BINARY_INIT))) static void setupProxyFunctions() {
         |  ${proxies.map(_._2.trim).mkString("\n  ")}
         |}
       """.stripMargin)
  }

  // Codegen for version header
  def tryParse(s: String, default: Int) = try { s.toInt } catch { case _: NumberFormatException => default }
  def cacheVersionHeader(cacheDirectory: File, tempTarget: File, finalTarget: File, version: String) = {
    val VersionRegex(major, minor, _, _, _, _) = version
    cachedGeneration(cacheDirectory, tempTarget, finalTarget,
      "#ifndef VERSION_H\n"+
      "#define VERSION_H\n"+
      "#define patchVersionMajor "+tryParse(major, -1)+"\n"+
      "#define patchVersionMinor "+tryParse(minor, -1)+"\n"+
      "#define patchFullVersion \""+version+"\"\n"+
      "#endif /* VERSION_H */"
    )
  }

  case class PatchFile(platform: String, version: String, file: File, buildId: String)
  object Keys {
    val patchBuildDir  = SettingKey[File]("native-patch-build-directory")
    val patchCacheDir  = SettingKey[File]("native-patch-cache-directory")
    val patchSourceDir = SettingKey[File]("native-patch-source-directory")

    val win32Directory = TaskKey[File]("native-patch-win32-directory")
    val macosDirectory = TaskKey[File]("native-patch-macos-directory")

    val steamrtSDL     = TaskKey[File]("native-patch-download-steam-runtime-sdl")
    val steamrtSDLDev  = TaskKey[File]("native-patch-download-steam-runtime-sdl-dev")

    val commonIncludes = TaskKey[File]("native-patch-common-includes")

    val nativeVersions = TaskKey[Seq[PatchFile]]("native-patch-files")
  }
  import Keys._

  // Patch build script
  val settings = Seq(
    patchBuildDir  := crossTarget.value / "native-patch-build",
    patchCacheDir  := patchBuildDir.value / "cache",
    patchSourceDir := baseDirectory.value / "src" / "patch" / "native",

    // prepare common directories
    commonIncludes := prepareDirectory(patchBuildDir.value / "common") { dir =>
      cacheVersionHeader(patchCacheDir.value / "version_h", patchBuildDir.value / "tmp_version.h",
                         dir / "version.h", version.value)
    },
    win32Directory := prepareDirectory(patchBuildDir.value / "win32") { dir =>
      cachedTransform(patchCacheDir.value / "win23_lua_stub",
        patchSourceDir.value / "win32" / "stub" / "lua51_Win32.c",
        dir / "lua51_Win32.dll")((in, out) => win32_cc(Seq("-shared", "-o", out, in)))
    },
    macosDirectory := prepareDirectory(patchBuildDir.value / "macos") { dir =>
      cachedTransform(patchCacheDir.value / "macos_proxy",
                      patchSourceDir.value / "macos" / "external_symbols",
                      dir / "extern_defines.c")(generateProxyDefine)
    },

    // Extract Steam runtime libSDL files.
    steamrtSDL :=
      extractSteamRuntime(baseDirectory.value / "project" / "contrib_bin" / config_steam_sdlbin_path,
                          patchBuildDir.value / config_steam_sdlbin_name,
                          streams.value.log.info("Extracting "+config_steam_sdlbin_name+"...")) { (dir, target) =>
        IO.copyFile(dir / "usr" / "lib" / "i386-linux-gnu" / config_steam_sdlbin_name, target)
      },
    steamrtSDLDev :=
      extractSteamRuntime(baseDirectory.value / "project" / "contrib_bin" / config_steam_sdldev_path,
                          patchBuildDir.value / "SDL2_include",
                          streams.value.log.info("Extracting SDL2 headers...")) { (dir, target) =>
        IO.copyDirectory(dir / "usr" / "include" / "SDL2", target)
      },
    nativeVersions := {
      val patchDirectory = patchBuildDir.value / "output"
      val logger         = streams.value.log

      IO.createDirectory(patchDirectory)

      val patches = for(versionDir <- (patchSourceDir.value / "versions").listFiles) yield {
        val version = versionDir.getName
        val Array(platform, sha256) = version.split("_")

        val (cc, nasmFormat, binaryExtension, sourcePath, extraCDeps, gccFlags, nasmFiles) =
          platform match {
            case "win32" => (win32_cc _, "win32"  , ".dll",
              Seq(patchSourceDir.value / "win32"),
              allFiles(win32Directory.value, ".dll"),
              Seq("-l", "lua51_Win32", "-Wl,-L,"+win32Directory.value,
                  "-static-libgcc", "-Wl,--start-group", "-lmsvcr90") ++ config_win32_secureFlags,
              Seq(patchSourceDir.value / "win32" / "proxy.s"))
            case "macos" => (macos_cc _, "macho32", ".dylib" ,
              Seq(patchSourceDir.value / "macos", patchSourceDir.value / "posix", macosDirectory.value), Seq(),
              Seq("-ldl", "-framework", "CoreFoundation", "-Wl,-segprot,MPPATCH_PROXY,rwx,rx"),
              Seq())
            case "linux" => (linux_cc _, "elf"  , ".so" ,
              Seq(patchSourceDir.value / "linux"  , patchSourceDir.value / "posix", steamrtSDLDev.value),
              Seq(steamrtSDL.value), Seq("-ldl"),
              Seq())
          }
        val fullSourcePath = Seq(patchSourceDir.value / "common", patchSourceDir.value / "inih",
                                 commonIncludes.value, versionDir) ++ sourcePath
        val fullIncludePath = fullSourcePath :+ LuaJITBuild.Keys.luajitIncludes.value
        val cBuildDependencies =
          fullSourcePath.flatMap(x => allFiles(x, ".c")) ++ fullIncludePath.flatMap(x => allFiles(x, ".h")) ++
          extraCDeps
        val sBuildDependencies = fullSourcePath.flatMap(x => allFiles(x, ".s")) ++ nasmFiles
        def includePaths(flag: String) = fullIncludePath.flatMap(x => Seq(flag, dir(x)))

        val versionStr = "version_"+version
        val buildTmp = patchBuildDir.value / versionStr
        IO.createDirectory(buildTmp)

        val nasmOut = trackDependencySet(patchCacheDir.value / (versionStr + "_nasm_o"), sBuildDependencies.toSet) {
          logger.info("Compiling as_entry.o for version "+version)

          (for(file <- nasmFiles) yield {
            val output = buildTmp / file.getName.replace(".s", ".o")
            nasm(includePaths("-i") ++ Seq("-Ox", "-f", nasmFormat, "-o", output, file))
            output
          }).toSet
        }

        val targetDir = patchDirectory / version
        val targetFile = targetDir / ("mppatch_"+version+binaryExtension)
        val buildIdFile = targetDir / "buildid.txt"
        val outputPath =
          trackDependencies(patchCacheDir.value / (versionStr + "_c_out"), cBuildDependencies.toSet ++ nasmOut) {
            logger.info("Compiling binary patch for version "+version)
            val buildId = UUID.randomUUID()

            if(targetDir.exists) IO.delete(targetDir)
            targetDir.mkdirs()

            IO.write(buildIdFile, buildId.toString)

            cc(includePaths("-I") ++ Seq(
              "-m32", "-g", "-shared", "-O2", "--std=gnu11", "-Wall", "-fvisibility=hidden",
              "-o", targetFile, "-DMPPATCH_CIV_VERSION=\""+sha256+"\"", "-DMPPATCH_PLATFORM=\""+platform+"\"",
              "-DMPPATCH_BUILDID=\""+buildId+"\"") ++ nasmOut ++ config_common_secureFlags ++
              gccFlags ++ fullSourcePath.flatMap(x => allFiles(x, ".c")))
            targetDir
          }

        PatchFile(platform, sha256, targetFile, IO.read(buildIdFile))
      }
      patches.toSeq
    }
  )
}
