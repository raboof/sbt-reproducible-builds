///*
// * Copyright 2017 Arnout Engelen
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package net.bzzt.reproduciblebuilds
//
//import com.typesafe.sbt.packager.universal.UniversalPlugin
//import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
//import sbt.Keys._
//import sbt._
//
///** Helper code for sbt-native-packager integration.
//  *
//  * The main ReproducibleBuildsPlugin code should not rely on any sbt-native-packager code, since this plugin should be
//  * usable without sbt-native-packager and not introduce it to projects not already using it.
//  *
//  * Any code dependent on sbt-native-packager classes should go here, and only be called in case it is indeed available.
//  *
//  * To avoid 'leaking' references to sbt-native-packager classes into other classes, care should be taken that none
//  * appear in parameter or return value types either.
//  */
//object SbtNativePackagerHelpers {
//  val plugin: Plugins.Basic = UniversalPlugin
//
//  val settings: Seq[Setting[_]] = Seq(
//    packageBin in Universal := {
//      (packageBin in Universal).?.value match {
//        case Some(zip) =>
//          ReproducibleBuildsPlugin.postProcessZip(zip)
//        case None =>
//          throw new IllegalStateException(
//            "Reference to undefined setting: packageBin in Universal. " +
//              "Have you enabled a sbt-native-packager plugin?"
//          )
//      }
//    }
//  )
//}
