/*
 * Copyright (c) 2015 Lymia Alusyia <lymia@lymiahugs.com>
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

package moe.lymia.multiverse.core.dlc

import java.nio.file.{Files, Path}
import java.util.{Locale, UUID}

import moe.lymia.multiverse.platform.Platform
import moe.lymia.multiverse.util.{Crypto, IOUtils}

import scala.xml.Node

case class DLCUISkin(name: String, set: String, platform: String, includeImports: Boolean,
                     skinSpecificDirectory: Map[String, Array[Byte]])
case class DLCInclude(event: String, fileData: Node)
case class DLCMap(extension: String, data: Array[Byte])
case class DLCData(id: UUID, version: Int, priority: Int, name: String, description: String,
                   gameplayIncludes: Seq[DLCInclude], globalIncludes: Seq[DLCInclude], mapEntries: Seq[DLCMap],
                   importFileList: Map[String, Array[Byte]], uiSkins: Seq[DLCUISkin])

object DLCKey {
  val staticInterlace =
    Seq(0x1f, 0x33, 0x93, 0xfb, 0x35, 0x0f, 0x42, 0xc7,
        0xbd, 0x50, 0xbe, 0x7a, 0xa5, 0xc2, 0x61, 0x81) map (_.toByte)
  val staticInterlaceStream = Stream.from(0) map (x => staticInterlace(x%staticInterlace.length))
  def interlaceData(data: Seq[Byte]) =
    data.zip(staticInterlaceStream).flatMap(x => Seq(x._1, x._2))

  def encodeLe32(i: Int) =
    Seq(i&0xFF, (i>>8)&0xFF, (i>>16)&0xFF, (i>>24)&0xFF)
  def encodeBe32(i: Int) = encodeLe32(i).reverse
  def encodeLe16(i: Int) =
    Seq(i&0xFF, (i>>8)&0xFF)
  def encodeUUID(u: UUID) =
    (encodeLe32(((u.getMostSignificantBits >>32) & 0xFFFFFFFF).toInt) ++
      encodeLe16(((u.getMostSignificantBits >>16) &     0xFFFF).toInt) ++
      encodeLe16(((u.getMostSignificantBits >> 0) &     0xFFFF).toInt) ++
      encodeBe32(((u.getLeastSignificantBits>>32) & 0xFFFFFFFF).toInt) ++
      encodeBe32(((u.getLeastSignificantBits>> 0) & 0xFFFFFFFF).toInt)).map(_.toByte)

  def encodeNumber(i: Int) = i.toString.getBytes("UTF8").toSeq
  def key(u: UUID, sid: Seq[Int], ptags: String*) = {
    val data = sid.map(encodeNumber) ++ ptags.map(_.getBytes("UTF8").toSeq)
    Crypto.md5_hex(interlaceData(encodeUUID(u) ++ data.fold(Seq())(_ ++ _)))
  }
}
object DLCDataWriter {
  private val languageList = Seq("en_US","fr_FR","de_DE","es_ES","it_IT","ru_RU","ja_JP","pl_PL","ko_KR","zh_Hant_HK")
  private def languageValues(string: String) = languageList.map { x =>
    <Value language={x}>{string}</Value>
  }

  private def commonHeader(name: String, id: UUID, version: Int) =
    <GUID>{"{"+id+"}"}</GUID>
    <Version>{version.toString}</Version>
    <Name> {languageValues(id.toString.replace("-", "")+"_v"+version)} </Name>
    <Description> {languageValues(name)} </Description>

    <SteamApp>99999</SteamApp>
    <Ownership>FREE</Ownership>
    <PTags>
      <Tag>Version</Tag>
      <Tag>Ownership</Tag>
    </PTags>
    <Key>{DLCKey.key(id, Seq(99999), version.toString, "FREE")}</Key>
  def writeDLC(dlcBasePath: Path, languageFilePath: Path, dlcData: DLCData, platform: Platform) = {
    var id = 0
    def newId() = {
      id = id + 1
      id
    }

    val nameString = dlcData.id.toString.replace("-", "") + "_v" + dlcData.version

    def writeIncludes(pathName: String, includes: Seq[DLCInclude]) = {
      val path = platform.resolve(dlcBasePath, pathName)
      if(includes.nonEmpty) Files.createDirectories(path)
      for(DLCInclude(event, fileData) <- includes) yield {
        val fileName = "mvmm_include_"+nameString+"_"+event+"_"+newId()+".xml"
        IOUtils.writeXML(platform.resolve(path, fileName), fileData)
        <NODE>{fileName}</NODE>.copy(label = event)
      }
    }
    def writeUISkins(skins: Seq[DLCUISkin]) =
      for(DLCUISkin(name, set, skinPlatform, includeImports, files) <- skins) yield {
        val dirName = "UISkin_"+name+"_"+set+"_"+skinPlatform
        val dirPath = platform.resolve(dlcBasePath, dirName)
        if(files.nonEmpty) Files.createDirectories(dirPath)
        for((name, file) <- files) IOUtils.writeFile(platform.resolve(dirPath, name), file)
        <UISkin name={name} set={set} platform={skinPlatform}>
          <Skin>
            { if(files.nonEmpty) Seq(<Directory>{dirName}</Directory>) else Seq() }
            { if(includeImports) Seq(<Directory>Files</Directory>) else Seq() }
          </Skin>
        </UISkin>
      }

    if(dlcData.mapEntries.nonEmpty) {
      val mapDirectory = platform.resolve(dlcBasePath, "Maps")
      Files.createDirectories(mapDirectory)
      for (DLCMap(extension, data) <- dlcData.mapEntries)
        IOUtils.writeFile(mapDirectory.resolve("mvmm_map_" + nameString + "_" + newId() + "." + extension), data)
    }
    val mapsTag = if(dlcData.mapEntries.nonEmpty) Seq(<MapDirectory>Maps</MapDirectory>) else Seq()

    val filesDirectory = platform.resolve(dlcBasePath, "Files")
    Files.createDirectories(filesDirectory)
    for((name, file) <- dlcData.importFileList) IOUtils.writeFile(platform.resolve(filesDirectory, name), file)

    IOUtils.writeXML(platform.resolve(dlcBasePath, nameString+".Civ5Pkg"), <Civ5Package>
      {commonHeader(dlcData.name, dlcData.id, dlcData.version)}

      <Priority>{dlcData.priority.toString}</Priority>

      {writeIncludes("GlobalImports", dlcData.globalIncludes)}
      <Gameplay>
        {writeIncludes("GameplayImports", dlcData.gameplayIncludes)}
        <Directory>Files</Directory>
        {if(dlcData.gameplayIncludes.nonEmpty) Seq(<Directory>GameplayImports</Directory>) else Seq()}
        {mapsTag}
      </Gameplay>

      { writeUISkins(dlcData.uiSkins) }
    </Civ5Package>)

    val uuid_string = dlcData.id.toString.replace("-", "").toUpperCase(Locale.ENGLISH)
    IOUtils.writeXML(languageFilePath, <GameData>
      {
        languageList.flatMap(x =>
          <NODE>
            <Row Tag={"TXT_KEY_"+uuid_string+"_NAME"}>
              <Text>{dlcData.id.toString.replace("-", "")+"_v"+dlcData.version}</Text>
            </Row>
            <Row Tag={"TXT_KEY_"+uuid_string+"_DESCRIPTION"}>
              <Text>{dlcData.name}</Text>
            </Row>
          </NODE>.copy(label = "Language_"+x)
        )
      }
    </GameData>)
  }
}