package net.bzzt.reproduciblebuilds

import java.net.URI

case class VerificationResult(
  uri: URI,
  ourSums: Map[String, Checksum],
  remoteSums: Map[String, Checksum],
 ) {
  def verdicts: Seq[(String, String)] =
   ourSums.map {
    case (key, value) => (key, verdict(key, value))
   }.toSeq ++
     ourSums.keySet.diff(remoteSums.keySet).map { missingInTheirs => (missingInTheirs, "Missing in their checksums but present in ours") } ++
     remoteSums.keySet.diff(ourSums.keySet).map { missingInOurs => (missingInOurs, "Missing in our checksums but present in theirs") }

  def verdict(filename: String, ourSum: Checksum): String = remoteSums.get(filename) match {
   case None => "Not found in remote attestation"
   case Some(checksum) =>
      if (checksum == ourSum) "Match"
      else s"Mismatch: our ${ourSum.hexChecksum} did not match their ${checksum.hexChecksum}"
  }

  def ok = ourSums == remoteSums
}

object VerificationResult {
 def apply(uri: URI, ourSums: Seq[Checksum], remoteSums: Seq[Checksum]) = {
   new VerificationResult(
    uri,
    groupByUnique(ourSums)(_.filename),
    groupByUnique(remoteSums)(_.filename),
   )
 }

 private def groupByUnique[K, V](elements: Seq[V])(f: V => K): Map[K, V] =
  elements.groupBy(f).map {
   case (key, Seq(value)) => (key, value)
   case (key, values) => throw new IllegalArgumentException(s"Found ${values.size} elements for key $key")
  }
}