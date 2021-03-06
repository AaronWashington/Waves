package com.wavesplatform.lang.v1

import java.util.concurrent.TimeUnit

import com.wavesplatform.lang.v1.CalculateAssetIdBenchmark.{CalculateAssetIdSt, CurveSt}
import com.wavesplatform.lang.v1.EnvironmentFunctionsBenchmark.{curve25519, randomBytes}
import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.lang.v1.traits.domain.Issue
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import scorex.crypto.hash.{Blake2b256, Keccak256, Sha256}
import scorex.crypto.signatures.{Curve25519, Signature}

@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Threads(1)
@Fork(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
class CalculateAssetIdBenchmark {
  @Benchmark
  def blake2b256_150Kb(st: CurveSt, bh: Blackhole): Unit =
    bh.consume(Blake2b256.hash(st.message150Kb))

  @Benchmark
  def sha256_150Kb(st: CurveSt, bh: Blackhole): Unit =
    bh.consume(Sha256.hash(st.message150Kb))

  @Benchmark
  def keccak256_150Kb(st: CurveSt, bh: Blackhole): Unit =
    bh.consume(Keccak256.hash(st.message150Kb))

  @Benchmark
  def sigVerify_150Kb(st: CurveSt, bh: Blackhole): Unit =
    bh.consume(Curve25519.verify(Signature @@ st.signature150Kb, st.message150Kb, st.publicKey))

  @Benchmark
  def calculateAssetId(st: CalculateAssetIdSt, bh: Blackhole): Unit =
    bh.consume(Issue.calculateId(Int.MaxValue, st.MaxAssetDescription, isReissuable = true, st.MaxAssetName, Long.MaxValue, Long.MaxValue, ByteStr(new Array[Byte](64))))
}

object CalculateAssetIdBenchmark {
  @State(Scope.Benchmark)
  class CurveSt {
    val (privateKey, publicKey) = curve25519.generateKeypair
    val message150Kb            = randomBytes(150 * 1024)
    val message32Kb             = randomBytes(32 * 1024)
    val signature150Kb          = curve25519.sign(privateKey, message150Kb)
    val signature32Kb           = curve25519.sign(privateKey, message32Kb)
  }

  @State(Scope.Benchmark)
  class CalculateAssetIdSt {
    val MaxAssetName: String        = "a" * 16
    val MaxAssetDescription: String = "a" * 1000
  }
}
