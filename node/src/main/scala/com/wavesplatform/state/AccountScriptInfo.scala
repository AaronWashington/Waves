package com.wavesplatform.state

import com.wavesplatform.account.PublicKey
import com.wavesplatform.lang.script.Script

case class AccountScriptInfo(
  publicKey: PublicKey,
  script: Script,
  maxComplexity: Long,
  complexitiesByEstimator: Map[Int, Map[String, Long]]
)
