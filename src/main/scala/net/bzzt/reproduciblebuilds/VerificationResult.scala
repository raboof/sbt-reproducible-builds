/*
 * Copyright 2017 Arnout Engelen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.bzzt.reproduciblebuilds

import java.net.URI

sealed trait Verdict {
  val description: String
}
case object Match extends Verdict {
  val description = "Match"
}
case object MissingInTheirs extends Verdict {
  val description = "Missing in theirs but present in ours"
}
case object MissingInOurs extends Verdict {
  val description = "Missing in ours but present in theirs"
}
case class Mismatch(our: Checksum, their: Checksum) extends Verdict {
  val description = s"Mismatch: our ${our.hexChecksum} did not match their ${their.hexChecksum}"
}

case class VerificationResult(
    uri: URI,
    ourSums: Map[String, Checksum],
    remoteSums: Map[String, Checksum]
) {
  def asMarkdown = {
    val artifactName = uri.toASCIIString.substring(uri.toASCIIString.lastIndexOf('/') + 1)
    s"""# `$artifactName`: ${if (ok) "OK" else "NOT OK"}
     |
     |${verdicts.map { case (filename, verdict) => s"- $filename: ${verdict.description}" }.mkString("\n")}
     |
     |""".stripMargin
  }

  /** filename -> verdict
    */
  def verdicts: Seq[(String, Verdict)] =
    ourSums.map { case (key, value) =>
      (key, verdict(key, value))
    }.toSeq ++
      remoteSums.keySet.diff(ourSums.keySet).map(missingInOurs => (missingInOurs, MissingInOurs))

  def verdict(filename: String, ourSum: Checksum): Verdict = remoteSums.get(filename) match {
    case None           => MissingInTheirs
    case Some(checksum) =>
      if (checksum == ourSum) Match
      else Mismatch(ourSum, checksum)
  }

  def ok = ourSums == remoteSums
}

object VerificationResult {
  def apply(uri: URI, ourSums: Seq[Checksum], remoteSums: Seq[Checksum]) =
    new VerificationResult(
      uri,
      groupByUnique(ourSums)(_.filename),
      groupByUnique(remoteSums)(_.filename)
    )

  private def groupByUnique[K, V](elements: Seq[V])(f: V => K): Map[K, V] =
    elements.groupBy(f).map {
      case (key, Seq(value)) => (key, value)
      case (key, values)     => throw new IllegalArgumentException(s"Found ${values.size} elements for key $key")
    }
}
