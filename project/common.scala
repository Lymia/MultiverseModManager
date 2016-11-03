import sbt.{IO, Process, _}

/*
 * Copyright (C) 2015-2016 Lymia Aluysia <lymiahugs@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

object Config {
  val config_scalaVersion = "2.11.8"

  val config_mingw_gcc     = "i686-w64-mingw32-gcc"
  val config_linux_gcc     = "gcc"
  val config_nasm          = "nasm"

  val version_baseVersion  = "0.1.0"

  val config_steam_sdlbin_path = "libsdl2_2.0.3+steamrt1+srt4_i386.deb"
  val config_steam_sdldev_path = "libsdl2-dev_2.0.3+steamrt1+srt4_i386.deb"
  val config_steam_sdlbin_name = "libSDL2-2.0.so.0"
}

object Utils {
  val VersionRegex = "([0-9]+)\\.([0-9]+)(\\.([0-9]+))?(-(.*))?".r // major.minor.patch-suffix

  // Process helper functions
  def assertProcess(i: Int) = if(i != 0) sys.error("Process returned non-zero return value! (ret: "+i+")")
  def runProcess   (p: Seq[Any]) = assertProcess(Process(p.map(_.toString)) !)
  def runProcess   (p: Seq[Any], cd: File) = assertProcess(Process(p.map(_.toString), cd) !)

  // Directory helpers
  def dir     (path: File) = path.toString + "/"
  def allFiles(path: File, extension: String) = path.listFiles.filter(_.getName.endsWith(extension)).toSeq

  def prepareDirectory(path: File)(fn: File => Unit) = {
    IO.createDirectory(path)
    fn(path)
    path
  }
  def simplePrepareDirectory(path: File) = prepareDirectory(path){ dir => }

  // Code generation helpers
  def cached(cacheDirectory: File, inStyle: FilesInfo.Style = FilesInfo.lastModified, // hack to fix ambigous overload
             outStyle: FilesInfo.Style = FilesInfo.exists)
            (fn: Set[File] => Set[File]) = FileFunction.cached(cacheDirectory, inStyle, outStyle)(fn)
  def trackDependencies(cacheDirectory: File, deps: Set[File],
                        inStyle: FilesInfo.Style = FilesInfo.lastModified,
                        outStyle: FilesInfo.Style = FilesInfo.exists)(fn: => File) = {
    val cache = cached(cacheDirectory, inStyle, outStyle) { _ =>
      Set(fn)
    }
    cache(deps).head
  }
  def cachedTransform(cacheDirectory: File, input: File, output: File,
                      inStyle: FilesInfo.Style = FilesInfo.lastModified,
                      outStyle: FilesInfo.Style = FilesInfo.exists)(fn: (File, File) => Unit) = {
    val cache = cached(cacheDirectory, inStyle, outStyle){ in =>
      fn(in.head, output)
      Set(output)
    }
    cache(Set(input))
    output
  }
  def cachedGeneration(cacheDirectory: File, tempTarget: File, finalTarget: File, data: String) = {
    IO.write(tempTarget, data)
    cachedTransform(cacheDirectory, tempTarget, finalTarget, inStyle = FilesInfo.hash)((in, out) =>
      IO.copyFile(in, out))
  }
}